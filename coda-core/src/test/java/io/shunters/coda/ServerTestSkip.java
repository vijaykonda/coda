package io.shunters.coda;

import io.shunters.coda.server.CodaServer;
import org.apache.log4j.xml.DOMConfigurator;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by mykidong on 2016-08-23.
 */
public class ServerTestSkip {

    private static Logger log = LoggerFactory.getLogger(ServerTestSkip.class);

    @Before
    public void init() throws Exception {
        java.net.URL url = new ServerTestSkip().getClass().getResource("/log4j-test.xml");
        System.out.println("log4j url: " + url.toString());

        DOMConfigurator.configure(url);
    }

    @Test
    public void run() throws Exception
    {
        CodaServer broker = new CodaServer(9911, 4);
        Thread t = new Thread(broker);
        t.start();

        Thread.sleep(Long.MAX_VALUE);
    }
}
