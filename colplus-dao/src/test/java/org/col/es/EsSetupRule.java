package org.col.es;

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Strings;

import org.col.common.util.YamlUtils;
import org.elasticsearch.client.RestClient;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pl.allegro.tech.embeddedelasticsearch.EmbeddedElastic;
import pl.allegro.tech.embeddedelasticsearch.PopularProperties;

public class EsSetupRule extends ExternalResource {

  private static final Logger LOG = LoggerFactory.getLogger(EsSetupRule.class);
  private static final String ES_VERSION = "6.4.1";

  private EmbeddedElastic ee;

  private EsConfig cfg;

  @Override
  protected void before() throws Throwable {
    super.before();
    cfg = YamlUtils.read(EsConfig.class, "/es-test.yaml");
    if (cfg.embedded()) {
      LOG.info("Starting embedded Elasticsearch");
      try {
        // use configured port or assign free ports using local socket 0
        int httpPort;
        if (Strings.isNullOrEmpty(cfg.ports)) {
          httpPort = getFreePort();
          cfg.ports = String.valueOf(httpPort);
        } else {
          httpPort = Integer.parseInt(cfg.ports);
        }
        int tcpPort = getFreePort();
        ee = EmbeddedElastic.builder().withInstallationDirectory(new File(cfg.hosts))
            .withElasticVersion(ES_VERSION).withStartTimeout(60, TimeUnit.SECONDS)
            .withSetting(PopularProperties.TRANSPORT_TCP_PORT, tcpPort)
            .withSetting(PopularProperties.HTTP_PORT, httpPort)
            // .withEsJavaOpts("-Xms128m -Xmx512m")
            .build().start();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    } else {
      LOG.info("Using external Elasticsearch server");
    }
  }

  public RestClient getEsClient() {
    return new EsClientFactory(cfg).createClient();
  }

  public EsConfig getEsConfig() {
    return cfg;
  }

  @Override
  protected void after() {
    super.after();
    if (ee != null) {
      ee.stop();
    }
  }
  
  private static int getFreePort() {
    try(ServerSocket ss = new ServerSocket(0)) {
      return ss.getLocalPort();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

//  private static int getFreePort(int preferredPort) {
//    for (int i = 0; i < 100; i++) {
//      preferredPort += i;
//      try {
//        ServerSocket ss = new ServerSocket(preferredPort);
//        ss.close();
//        return preferredPort;
//      } catch (BindException e) {
//        // ...
//      } catch (IOException e) {
//        throw new RuntimeException(e);
//      }
//    }
//    throw new RuntimeException("No free port available after 100 attempts");
//  }

}