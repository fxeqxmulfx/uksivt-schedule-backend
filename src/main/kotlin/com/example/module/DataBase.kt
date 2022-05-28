package com.example.module

import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgConnectOptions
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.*


object DataBase {
    private val connectOptions = PgConnectOptions()
        .setPort(5432)
        .setHost(System.getenv("pg_host"))
        .setDatabase(System.getenv("pg_database"))
        .setUser(System.getenv("pg_user"))
        .setPassword(System.getenv("pg_password"))

    private val poolOptions = PoolOptions()
        .setMaxSize(20)

    fun getClient(): SqlClient {
        return PgPool.client(connectOptions, poolOptions)
    }

    suspend fun fetchAll(sql: String, tuple: Tuple): RowSet<Row> {
        val client = getClient()
        try {
            return client.preparedQuery(sql).execute(tuple).await()
        } finally {
            client.close().await()
        }
    }
}