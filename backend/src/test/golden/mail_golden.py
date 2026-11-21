#!/usr/bin/env python3
"""Golden REST capture for hitorro-fleet-mail-analytics (see run.sh).
Starts fleet-retrieval (--retrieval-cp, standalone, with the mail_email type) holding a 'mail' index,
a logging HTTP proxy in front of it, a webhook capture server, and the mail-analytics app (--cp); runs
the request list, records for every request the response AND the requests mail-analytics made to
fleet-retrieval and to webhooks meanwhile, and at the end the H2 tables (via the h2 jar)."""
import argparse, re, glob, http.server, json, os, shutil, socket, socketserver, subprocess, sys, tempfile, threading, time, urllib.request, urllib.error

def repl(text, k, v):
    """Masks a run value: the work dir anywhere, a port only as ':<port>' (not inside other numbers)."""
    if k.isdigit():
        return re.sub(r'(?<=:)' + k + r'(?!\d)', v, text)
    return text.replace(k, v)

def free_port():
    s = socket.socket(); s.bind(('127.0.0.1', 0)); p = s.getsockname()[1]; s.close(); return p

def call(method, url, body=None, ctype='application/json', timeout=60, accept=None):
    data = None
    if body is not None:
        data = body.encode() if isinstance(body, str) else json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, method=method)
    if data is not None: req.add_header('Content-Type', ctype)
    if accept: req.add_header('Accept', accept)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.headers.get('Content-Type', ''), r.read().decode('utf-8', 'replace')
    except urllib.error.HTTPError as e:
        return e.code, e.headers.get('Content-Type', ''), e.read().decode('utf-8', 'replace')
    except Exception as e:
        return -1, '', 'EXC ' + type(e).__name__ + ' ' + str(e)

LOG = []          # captured outgoing requests (proxy + webhooks)
LOCK = threading.Lock()

def make_handler(kind, upstream=None):
    class H(http.server.BaseHTTPRequestHandler):
        protocol_version = 'HTTP/1.1'
        def log_message(self, *a): pass
        def _do(self):
            n = int(self.headers.get('Content-Length') or 0)
            body = self.rfile.read(n) if n else b''
            if self.headers.get('Transfer-Encoding', '').lower() == 'chunked':
                body = b''
                while True:
                    size = int(self.rfile.readline().strip() or b'0', 16)
                    if size == 0: self.rfile.readline(); break
                    body += self.rfile.read(size); self.rfile.readline()
            hdrs = {k: v for k, v in self.headers.items() if k.lower() in ('content-type', 'x-signature', 'x-test', 'accept')}
            if kind == 'proxy':
                req = urllib.request.Request(upstream + self.path, data=body or None, method=self.command)
                for k, v in self.headers.items():
                    if k.lower() in ('content-type', 'accept'): req.add_header(k, v)
                try:
                    with urllib.request.urlopen(req, timeout=60) as r:
                        st, ct, out = r.status, r.headers.get('Content-Type', ''), r.read()
                except urllib.error.HTTPError as e:
                    st, ct, out = e.code, e.headers.get('Content-Type', ''), e.read()
            else:
                st, ct, out = 200, 'application/json', b'{"ok":true}'
            with LOCK:
                LOG.append({"to": kind, "method": self.command, "path": self.path, "headers": hdrs,
                            "body": body.decode('utf-8', 'replace'), "status": st})
            self.send_response(st)
            if ct: self.send_header('Content-Type', ct)
            self.send_header('Content-Length', str(len(out)))
            self.end_headers(); self.wfile.write(out)
        do_GET = do_POST = do_PUT = do_DELETE = _do
    return H

class TServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True

def wait_up(url, proc, secs=180):
    t = time.time()
    while time.time() - t < secs:
        st, _, b = call('GET', url, timeout=3)
        if st == 200 and ('UP' in b or 'ok' in b): return True
        if proc.poll() is not None: return False
        time.sleep(1)
    return False

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--cp', required=True); ap.add_argument('--retrieval-cp', required=True)
    ap.add_argument('--requests', required=True); ap.add_argument('--out', required=True)
    ap.add_argument('--h2-jar', required=True); ap.add_argument('--config', required=True)
    ap.add_argument('--types', required=True, help='dir with mail_email.json etc.')
    ap.add_argument('--work')
    ap.add_argument('--ui', help='write a headless UI check (Playwright, see ui_routes.js) to this file')
    ap.add_argument('--ui-dir', help='directory with ui_routes.js and node_modules/playwright')
    a = ap.parse_args()
    work = a.work or tempfile.mkdtemp(prefix='mailgold-')
    shutil.rmtree(work, ignore_errors=True); os.makedirs(work); work = os.path.realpath(work)
    import runpy; spec = runpy.run_path(a.requests)
    procs = []
    def java(cp, main_cls, args, log, home):
        cmd = ['java', '-Xmx1g', '-Duser.home=' + home] + (['-jar', cp] if (cp.endswith('.jar') and os.pathsep not in cp) else ['-cp', cp, main_cls]) + args
        env = dict(os.environ, HT_BIN=os.path.join(work, 'bin'))
        p = subprocess.Popen(cmd, stdout=open(log, 'w'), stderr=subprocess.STDOUT, cwd=work, env=env); procs.append(p); return p
    servers = []
    try:
        shutil.copytree(a.config, os.path.join(work, 'bin', 'config'))
        for f in glob.glob(os.path.join(a.types, '*.json')): shutil.copy(f, os.path.join(work, 'bin', 'config', 'types'))
        rport = free_port()
        rp = java(a.retrieval_cp, 'com.hitorro.fleet.retrieval.FleetRetrievalApplication', [f'--server.port={rport}',
                  '--hitorro.fleet.retrieval.mode=standalone', '--hitorro.fleet.retrieval.ollama.enabled=false',
                  f'--hitorro.fleet.retrieval.standalone-lucene-home={work}/r/lucene', f'--hitorro.fleet.retrieval.standalone-kv-home={work}/r/kv'],
                  os.path.join(work, 'retrieval.log'), os.path.join(work, 'rhome'))
        rbase = f'http://127.0.0.1:{rport}'
        if not wait_up(rbase + '/actuator/health', rp): print('retrieval did not come up', work, file=sys.stderr); sys.exit(2)
        now_ms = int(time.time() * 1000)
        for m, path, body in spec['seed'](now_ms):
            st, _, b = call(m, rbase + path, body)
            if st != 200: print('seed failed', path, st, b[:300], file=sys.stderr); sys.exit(3)
        pport, wport = free_port(), free_port()
        for port, kind, up in ((pport, 'proxy', rbase), (wport, 'webhook', None)):
            srv = TServer(('127.0.0.1', port), make_handler(kind, up)); servers.append(srv)
            threading.Thread(target=srv.serve_forever, daemon=True).start()
        aport = free_port()
        apphome = os.path.join(work, 'home')
        ap_ = java(a.cp, 'com.hitorro.fleet.mailanalytics.MailAnalyticsApplication', [f'--server.port={aport}',
                   f'--mailanalytics.retrieval.base-url=http://127.0.0.1:{pport}'] + spec['ARGS'](work),
                   os.path.join(work, 'app.log'), apphome)
        base = f'http://127.0.0.1:{aport}'
        if not wait_up(base + '/actuator/health', ap_): print('app did not come up', work, file=sys.stderr); sys.exit(2)
        time.sleep(2)
        with LOCK: LOG.clear()
        subst = {work: '<WORK>', str(aport): '<PORT>', str(pport): '<RPORT>', str(wport): '<WPORT>', str(rport): '<RPORT>'}
        def sub(x):
            if x is None: return None
            for k, v in subst.items(): x = repl(x, k, v)
            return x
        records = []
        for i, (m, path, body) in enumerate(spec['requests'](work, f'http://127.0.0.1:{wport}/hook')):
            if m == 'SLEEP': time.sleep(float(path)); continue
            if m == 'POSTTEXT': st, ct, b = call('POST', base + path, body, ctype='text/plain')
            else: st, ct, b = call(m, base + path, body)
            time.sleep(0.3)
            with LOCK: out = list(LOG); LOG.clear()
            rb = body if isinstance(body, (str, type(None))) else json.dumps(body, ensure_ascii=False)
            records.append({"i": i, "req": sub(f'{m} {path}'), "body": sub(rb), "status": st, "ctype": ct, "resp": sub(b),
                            "upstream": sub(json.dumps(out, ensure_ascii=False))})
        if a.ui:
            routes = ['/', '/search', '/threads', '/topics', '/saved', '/alerts', '/inbox', '/reports', '/suggestions',
                      '/settings', '/senders/ada@acme.com', '/domains/acme.com', '/no-such-route']
            subprocess.run(['node', 'ui_routes.js', base, a.ui] + routes, cwd=a.ui_dir, timeout=600)
        # H2 tables (the DB runs with AUTO_SERVER=TRUE, so a second process can read it)
        dbfile = os.path.join(apphome, 'hthome', 'mailanalytics', 'db', 'mailanalytics')
        url = f'jdbc:h2:file:{dbfile};AUTO_SERVER=TRUE;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE'
        for t in spec['TABLES']:
            r = subprocess.run(['java', '-cp', a.h2_jar, os.path.join(os.path.dirname(os.path.abspath(__file__)), 'H2Dump.java'), url, t],
                               capture_output=True, text=True, timeout=120)
            records.append({"i": len(records), "req": f'TABLE {t}', "body": None, "status": r.returncode, "ctype": 'table',
                            "resp": sub(r.stdout + r.stderr), "upstream": '[]'})
        with open(a.out, 'w') as fh:
            for r in records: fh.write(json.dumps(r, ensure_ascii=False) + '\n')
        print('wrote', len(records), 'records to', a.out)
    finally:
        for s in servers: s.shutdown()
        for p in procs: p.terminate()
        for p in procs:
            try: p.wait(20)
            except Exception: p.kill()

if __name__ == '__main__': main()
