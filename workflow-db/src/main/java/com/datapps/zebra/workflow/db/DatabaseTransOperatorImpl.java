/*
 * Copyright 2017 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.datapps.zebra.workflow.db;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.log4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;


/**
 * This class should be only used inside {@link DatabaseOperatorImpl}, then we remove public.
 */
class DatabaseTransOperatorImpl implements DatabaseTransOperator {

  private static final Logger logger = Logger.getLogger(DatabaseTransOperator.class);
  private final Connection conn;
  private final QueryRunner queryRunner;

  public DatabaseTransOperatorImpl(final QueryRunner queryRunner, final Connection conn) {
    this.conn = conn;
    this.queryRunner = queryRunner;
  }

  /**
   * The ID that was generated is maintained in Mysql server on a per-connection basis.
   * This means that the value returned by the function to a given client is
   * the first AUTO_INCREMENT value generated for most recent statement
   *
   * This value cannot be affected by other callers, even if they generate
   * AUTO_INCREMENT values of their own.
   *
   * @return last insertion ID
   */
  @Override
  public long getLastInsertId() throws SQLException {
    // A default connection: autocommit = true.
    long num = -1;
    try {
      num = ((Number) this.queryRunner
          .query(this.conn, "SELECT LAST_INSERT_ID();", new ScalarHandler<>(1)))
          .longValue();
    } catch (final SQLException ex) {
      logger.error("can not get last insertion ID");
      throw ex;
    }
    return num;
  }


  @Override
  public <T> T query(final String querySql, final ResultSetHandler<T> resultHandler,
      final Object... params)
      throws SQLException {
    try {
      return this.queryRunner.query(this.conn, querySql, resultHandler, params);
    } catch (final SQLException ex) {
      //RETRY Logic should be implemented here if needed.
      throw ex;
    } finally {
      // Note: CAN NOT CLOSE CONNECTION HERE.
    }
  }

  @Override
  public int update(final String updateClause, final Object... params) throws SQLException {
    try {
      return this.queryRunner.update(this.conn, updateClause, params);
    } catch (final SQLException ex) {
      //RETRY Logic should be implemented here if needed.
      throw ex;
    } finally {
      // Note: CAN NOT CLOSE CONNECTION HERE.
    }
  }

  @Override
  public Connection getConnection() {
    return this.conn;
  }
}
