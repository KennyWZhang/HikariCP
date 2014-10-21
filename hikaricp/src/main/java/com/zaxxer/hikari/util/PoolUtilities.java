package com.zaxxer.hikari.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

public final class PoolUtilities
{
   private static volatile boolean IS_JDBC4;
   private static volatile boolean jdbc4checked; 

   /**
    * Close connection and eat any exception.
    *
    * @param connection the connection to close
    */
   public static void quietlyCloseConnection(final Connection connection)
   {
      if (connection != null) {
         try {
            connection.close();
         }
         catch (SQLException e) {
            return;
         }
      }
   }

   /**
    * Get the elapsed time in millisecond between the specified start time and now.
    *
    * @param start the start time
    * @return the elapsed milliseconds
    */
   public static long elapsedTimeMs(final long start)
   {
      return System.currentTimeMillis() - start;
   }

   /**
    * Execute the user-specified init SQL.
    *
    * @param connection the connection to initialize
    * @param sql the SQL to execute
    * @throws SQLException throws if the init SQL execution fails
    */
   public static void executeSqlAutoCommit(final Connection connection, final String sql) throws SQLException
   {
      if (sql != null) {
         connection.setAutoCommit(true);
         try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
         }
      }
   }

   /**
    * Sleep and transform an InterruptedException into a RuntimeException.
    *
    * @param millis the number of milliseconds to sleep
    */
   public static void quietlySleep(final long millis)
   {
      try {
         Thread.sleep(millis);
      }
      catch (InterruptedException e) {
         throw new RuntimeException(e);
      }
   }

   @SuppressWarnings("unchecked")
   public static <T> T createInstance(String className, Class<T> clazz, Object... args)
   {
      if (className == null) {
         return null;
      }

      try {
         Class<?> loaded = PoolUtilities.class.getClassLoader().loadClass(className);

         Class<?>[] argClasses = new Class<?>[args.length];
         for (int i = 0; i < args.length; i++) {
            argClasses[i] = args[i].getClass();
         }

         if (args.length > 0) {
            Constructor<?> constructor = loaded.getConstructor(argClasses);
            return (T) constructor.newInstance(args);
         }

         return (T) loaded.newInstance();
      }
      catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   /**
    * Create/initialize the underlying DataSource.
    *
    * @return the DataSource
    */
   public static DataSource initializeDataSource(String dsClassName, DataSource dataSource, Properties dataSourceProperties, String jdbcUrl, String username, String password)
   {
      if (dataSource == null && dsClassName != null) {
         dataSource = createInstance(dsClassName, DataSource.class);
         PropertyBeanSetter.setTargetFromProperties(dataSource, dataSourceProperties);
         return dataSource;
      }
      else if (jdbcUrl != null) {
         return new DriverDataSource(jdbcUrl, dataSourceProperties, username, password);
      }

      return dataSource;
   }

   public static int getTransactionIsolation(String transactionIsolationName)
   {
      if (transactionIsolationName != null) {
         try {
            Field field = Connection.class.getField(transactionIsolationName);
            return field.getInt(null);
         }
         catch (Exception e) {
            throw new IllegalArgumentException("Invalid transaction isolation value: " + transactionIsolationName);
         }
      }

      return -1;
   }

   /**
    * Create a ThreadPoolExecutor.
    *
    * @param queueSize the queue size
    * @param threadName the thread name
    * @param threadFactory an optional ThreadFactory
    * @return a ThreadPoolExecutor
    */
   public static ThreadPoolExecutor createThreadPoolExecutor(final int queueSize, final String threadName, ThreadFactory threadFactory, RejectedExecutionHandler policy)
   {
      if (threadFactory == null) {
         threadFactory = new DefaultThreadFactory(threadName, true);
      }

      LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>(queueSize);
      ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 5, TimeUnit.SECONDS, queue, threadFactory, policy);
      executor.allowCoreThreadTimeOut(true);
      return executor;
   }

   public static boolean isJdbc41Compliant(Connection connection)
   {
      if (jdbc4checked) {
         return IS_JDBC4;
      }

      try {
         connection.getNetworkTimeout();  // This will throw AbstractMethodError or SQLException in the case of a non-JDBC 41 compliant driver
         IS_JDBC4 = true;
      }
      catch (AbstractMethodError | SQLException e) {
         IS_JDBC4 = false;
      }

      jdbc4checked = true;
      return IS_JDBC4;
   }
}
