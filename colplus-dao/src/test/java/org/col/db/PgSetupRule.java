package org.col.db;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.common.util.YamlUtils;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.yandex.qatools.embed.postgresql.EmbeddedPostgres;

/**
 * A junit test rule that starts up an {@link EmbeddedPostgres} server together
 * with a {@link HikariDataSource} and stops both at the end. The rule was
 * designed to share the server across all tests of a test class if it runs as a
 * static {@link org.junit.ClassRule}.
 *
 * It can even be used to share the same postgres server across several test
 * classes if it is used in as a {@link org.junit.ClassRule} in a TestSuite.
 *
 * By default it uses an embedded postgres server.
 * Setting the System variable "" to true enables the use of
 */
public class PgSetupRule extends ExternalResource {
  private static final Logger LOG = LoggerFactory.getLogger(PgSetupRule.class);

	private static EmbeddedColPg postgres;
	private static HikariDataSource dataSource;
	private static SqlSessionFactory sqlSessionFactory;
  private static PgConfig cfg;
	private boolean startedHere = false;

  public static PgConfig getCfg() {
    return cfg;
  }

  public static Connection getConnection() throws SQLException {
		return dataSource.getConnection();
	}

	public static SqlSessionFactory getSqlSessionFactory() {
		return sqlSessionFactory;
	}

	@Override
	protected void before() throws Throwable {
		super.before();
		if (postgres == null) {
			startDb();
			startedHere = true;
			initDb(cfg);
		}
	}

	private void startDb() {
		try {
      cfg = YamlUtils.read(PgConfig.class, "/pg-test.yaml");
			if (cfg.host == null) {
				postgres = new EmbeddedColPg(cfg);
        postgres.start();

			} else {
				LOG.info("Use external Postgres server {}/{}", cfg.host, cfg.database);
			}

			HikariConfig hikari = cfg.hikariConfig();
			hikari.setAutoCommit(false);
			dataSource = new HikariDataSource(hikari);

			// configure single mybatis session factory
			sqlSessionFactory = MybatisFactory.configure(dataSource, "test");

		} catch (Exception e) {
      LOG.error("Pg startup error: {}", e.getMessage(), e);

			if (dataSource != null) {
				dataSource.close();
			}
			if (postgres != null) {
				postgres.stop();
			}
			throw new RuntimeException(e);
		}
	}

	public static void initDb(PgConfig cfg) {
		try (Connection con = cfg.connect()) {
			System.out.println("Init empty database schema\n");
			ScriptRunner runner = new ScriptRunner(con);
			// needed to honor the $$ escapes in pg functions
      runner.setSendFullScript(true);
      runner.runScript(Resources.getResourceAsReader(PgConfig.SCHEMA_FILE));
			con.commit();

		} catch (SQLException | IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void after() {
		if (startedHere) {
			System.out.println("Shutdown dbpool");
			dataSource.close();
			if (postgres != null) {
				System.out.println("Stopping Postgres");
				postgres.stop();
			}
		}
	}

}