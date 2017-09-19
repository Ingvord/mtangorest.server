package org.tango.web.server.tomcat;

import org.apache.catalina.loader.WebappLoader;
import org.apache.catalina.startup.Tomcat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;

/**
 * @author Igor Khokhriakov <igor.khokhriakov@hzg.de>
 * @since 9/18/17
 */
public class WebappConfiguration {
    private final Logger logger = LoggerFactory.getLogger(WebappConfiguration.class);

    private final String webappPath;

    public WebappConfiguration(String webappPath) {
        this.webappPath = webappPath + "/webapp.war";
    }

    public void configure(Tomcat tomcat){
        logger.debug("Add webapp[tango] tomcat for device");
        org.apache.catalina.Context context =
                null;
        try {
            context = tomcat.addWebapp("tango", webappPath);
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }

        WebappLoader loader =
                new WebappLoader(Thread.currentThread().getContextClassLoader());
        loader.setDelegate(true);
        context.setLoader(loader);
    }
}