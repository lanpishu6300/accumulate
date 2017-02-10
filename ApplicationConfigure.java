package me.ele.bpm.runshop.impl.config;

import java.util.Properties;

import javax.sql.DataSource;

import me.ele.bpm.runshop.impl.utils.ProjectConstant;
import me.ele.elog.Log;
import me.ele.elog.LogFactory;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.autoproxy.BeanNameAutoProxyCreator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import com.zaxxer.hikari.HikariDataSource;

/**
 * 系统初始化信息类 初始化了数据库，事务配置信息
 * 
 * @author wangmingxiang
 * @since 2016-01-22
 */
@Configuration
@EnableTransactionManagement(proxyTargetClass = true)
@EnableConfigurationProperties({ DataSourceProperties.class })
@MapperScan(sqlSessionFactoryRef = "sqlSessionFactory", basePackages = ApplicationProperties.BASE_PACKAGE)
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableRabbit
@EnableAsync(proxyTargetClass = true)
public class ApplicationConfigure {

	private static final Log logger = LogFactory.getLog(ApplicationConfigure.class);

	/**
	 * 数据库配置信息
	 */
	@Autowired
	private DataSourceProperties dataSourceProperties;

	@Autowired
	private RedisConfig redisConfig;

	@Value("${redis.redisHost}")
	// @Value("192.168.112.36")
	private String redisHost;

	@Value("${redis.redisPort}")
	// @Value("6379")
	private int redisPort;

	@Value("${redis.redisTimeout}")
	// @Value("300")
	private int redisTimeout;
	// amqp initializztion starts

	@Autowired
	private AmqpProperties amqpProperties;

	@Bean(name = "connectionFactory")
	public ConnectionFactory connectionFactory() {
		logger.info("ApplicationConfigure.connectionFactory(): Start to initialize connecFactory.");
		CachingConnectionFactory connectionFactory = new CachingConnectionFactory(amqpProperties.getHost(),
				amqpProperties.getPort());
		connectionFactory.setUsername(amqpProperties.getUsername());
		connectionFactory.setPassword(amqpProperties.getPassword());
		connectionFactory.setVirtualHost(amqpProperties.getVirtualHost());
		logger.info("ApplicationConfigure.connectionFactory(): Done with initialize connecFactory.");
		return connectionFactory;
	}

	@Bean
	public SimpleRabbitListenerContainerFactory simpleRabbitListenerContainerFactory() {
		logger.info("ApplicationConfigure.simpleRabbitListenerContainerFactory(): Start to init simpleRabbitListenerContainerFactory.");
		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
		factory.setConnectionFactory(connectionFactory());
		factory.setConcurrentConsumers(amqpProperties.getConcurrentConsumers());
		factory.setMaxConcurrentConsumers(amqpProperties.getMaxConcurrentConsumers());
		return factory;

	}

	@Bean(name = "amqpTemplate")
	public AmqpTemplate RabbitTemplate() {
		logger.info("ApplicationConfigure.RabbitTemplate(): Start to init amqpTemplate.");
		RabbitTemplate template = new RabbitTemplate(connectionFactory());
		RetryTemplate retryTemplate = new RetryTemplate();
		ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();

		// set back off policy in case of broker connectivity error
		// starting with version 1.4
		backOffPolicy.setInitialInterval(ProjectConstant.BACKOFF_POLICY_INITIAL_INTERVAL);
		backOffPolicy.setMultiplier(ProjectConstant.BACKOFF_POLICY_MULTIPLIER);
		backOffPolicy.setMaxInterval(ProjectConstant.BACKOFF_POLICY_MAX_INTERVAL);

		retryTemplate.setBackOffPolicy(backOffPolicy);
		template.setRetryTemplate(retryTemplate);

		// set exchange
		template.setExchange(amqpProperties.getSenderExchangeName());

		return template;
	}

	// amqp initialization ends

	/**
	 * 初始化数据库连接池信息
	 * 
	 * @return
	 */
	@Bean(name = "dataSource", destroyMethod = "close")
	@Primary
	public DataSource dataSource() {
		logger.debug(
				"init datasource, url:{},username:{},password:{},driver:{},maxLifetime:{},maxPoolSize:{},minIdle:{},connectionTimeout:{}",
				dataSourceProperties.getJdbcUrl(), dataSourceProperties.getUsername(),
				dataSourceProperties.getPassword(), dataSourceProperties.getDriverClassName(),
				dataSourceProperties.getMaxLifetime(), dataSourceProperties.getMaximumPoolSize(),
				dataSourceProperties.getMinimumIdle(), dataSourceProperties.getConnectionTimeout());
		return new HikariDataSource(dataSourceProperties);
	}

	/**
	 * 初始化mybatis sql session工厂类
	 * 
	 * @return
	 * @throws Exception
	 */
	@Bean(name = "sqlSessionFactory")
	@Primary
	public SqlSessionFactory sqlSessionFactory() throws Exception {
		logger.debug("init mybatis sqlSessionFactory");
		SqlSessionFactoryBean sqlSessionFactoryBean = new SqlSessionFactoryBean();
		sqlSessionFactoryBean.setDataSource(dataSource());
		ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
		Resource[] resources = resourcePatternResolver.getResources(ApplicationProperties.MAPPER_LOCATION);
		sqlSessionFactoryBean.setMapperLocations(resources);
		return sqlSessionFactoryBean.getObject();
	}

	/**
	 * 初始化spring事务管理器
	 * 
	 * @return
	 */
	@Bean(name = "transactionManager")
	@Primary
	public PlatformTransactionManager transactionManager() {
		DataSourceTransactionManager transactionManager = new DataSourceTransactionManager();
		transactionManager.setDataSource(dataSource());
		return transactionManager;
	}

	/**
	 * 初始化事物AOP
	 * 
	 * @return
	 */
	@Bean(name = "transactionInterceptor")
	@Primary
	public TransactionInterceptor transactionInterceptor() {
		TransactionInterceptor transactionInterceptor = new TransactionInterceptor();
		transactionInterceptor.setTransactionManager(transactionManager());
		Properties transactionAttributes = new Properties();
		transactionAttributes.setProperty("*", "PROPAGATION_REQUIRED,-Exception");
		transactionInterceptor.setTransactionAttributes(transactionAttributes);
		return transactionInterceptor;
	}

	/**
	 * 初始化事物AOP
	 *
	 * @return
	 */
	@Bean(name = "requiredNewtransactionInterceptor")
	@Primary
	public TransactionInterceptor requiredNewtransactionInterceptor() {
		TransactionInterceptor transactionInterceptor = new TransactionInterceptor();
		transactionInterceptor.setTransactionManager(transactionManager());
		Properties transactionAttributes = new Properties();
		transactionAttributes.setProperty("*", "PROPAGATION_REQUIRES_NEW,-Exception");
		transactionInterceptor.setTransactionAttributes(transactionAttributes);
		return transactionInterceptor;
	}

	/**
	 * 初始化事物代理类 用于拦截指定名称类型的java类
	 * 
	 * @return
	 */
	@Bean(name = "beanNameAutoProxyCreator")
	@Primary
	public BeanNameAutoProxyCreator beanNameAutoProxyCreator() {
		BeanNameAutoProxyCreator beanNameAutoProxyCreator = new BeanNameAutoProxyCreator();
		beanNameAutoProxyCreator.setProxyTargetClass(true);
		beanNameAutoProxyCreator.setBeanNames("*Mapper");
		beanNameAutoProxyCreator.setInterceptorNames("transactionInterceptor");


		return beanNameAutoProxyCreator;
	}

	@Bean(name = "requiredNewTransactionAutoProxyCreator")
	@Primary
	public BeanNameAutoProxyCreator requiredNewTransactionAutoProxyCreator() {
		BeanNameAutoProxyCreator beanNameAutoProxyCreator = new BeanNameAutoProxyCreator();
		beanNameAutoProxyCreator.setProxyTargetClass(true);
		beanNameAutoProxyCreator.setBeanNames("*RequiredNewTransaction");
		beanNameAutoProxyCreator.setInterceptorNames("requiredNewtransactionInterceptor");

		return beanNameAutoProxyCreator;
	}

	@Bean(name = "jedisPoolConfig")
	public JedisPoolConfig jedisPoolConfig() {
		JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
		jedisPoolConfig.setMaxIdle(redisConfig.getMaxIdle());
		jedisPoolConfig.setMinIdle(redisConfig.getMinIdle());
		jedisPoolConfig.setMaxWaitMillis(redisConfig.getMaxWaitMillis());
		jedisPoolConfig.setTestOnBorrow(redisConfig.getTestOnBorrow());
		jedisPoolConfig.setTestOnReturn(redisConfig.getTestOnReturn());
		return jedisPoolConfig;
	}

	@Bean(name = "jedisPool")
	public JedisPool jedisPool() {
		return new JedisPool(redisConfig, redisHost, redisPort, redisTimeout);
	}

}
