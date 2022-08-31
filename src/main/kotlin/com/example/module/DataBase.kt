package com.example.module

import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgConnectOptions
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.*


object DataBase {
    private val connectOptions = PgConnectOptions()
        .setPort(5432)
        .setHost(System.getenv("PG_HOST"))
        .setDatabase(System.getenv("PG_DB"))
        .setUser(System.getenv("PG_USER"))
        .setPassword(System.getenv("PG_PASSWORD"))

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