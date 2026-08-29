/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

/**
 * Serves the SPA's index.html for any GET that isn't an API/actuator call
 * and doesn't look like a filename. Path-with-dot (index.html, foo.js,
 * favicon.ico) falls through to the static-resource handler; path-without
 * -dot (/inbox, /senders/foo@bar.com) forwards to /index.html so React
 * Router owns client routing.
 *
 * <p>Uses a @RestController + explicit path list rather than a
 * ViewController + regex to avoid the recursive-forward pitfall.</p>
 */
@RestController
@Configuration
public class SpaFallbackConfig {

    // First segment must NOT contain a dot (so index.html, foo.js at the
    // root hit the static resolver, not us). Later segments may — SPA
    // routes like /senders/foo@bar.com have dots in the domain part.
    // Static resource resolution runs before controller mapping, so real
    // assets under /assets/**, /static/**, favicon.ico etc. still resolve
    // before we're even consulted.
    @GetMapping({
            "/{p1:(?!api$|actuator$|assets$)[^\\.]+}",
            "/{p1:(?!api$|actuator$|assets$)[^\\.]+}/**"
    })
    public ModelAndView spa() {
        return new ModelAndView("forward:/index.html");
    }
}
