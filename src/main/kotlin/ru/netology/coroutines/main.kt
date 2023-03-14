package ru.netology.coroutines

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import ru.netology.coroutines.dto.*
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private val gson = Gson()
private const val BASE_URL = "http://127.0.0.1:9999"
private val client = OkHttpClient.Builder()
    .addInterceptor(HttpLoggingInterceptor(::log).apply {
        level = HttpLoggingInterceptor.Level.NONE
    })
    .connectTimeout(30, TimeUnit.SECONDS)
    .build()


fun main() {
    CoroutineScope(Dispatchers.Default)
        .launch {
            try {
                val posts = getPosts(client)
                    .map { post ->
                        async {
                            val commentsWithAuthor = async {
                                getComments(client, post.id)
                                    .map { comment ->
                                        async {
                                            CommentWithAuthor(
                                                comment,
                                                getAuthor(client, comment.authorId, "comment")
                                            )
                                        }
                                    }
                            }

                            val postAuthor = async {
                                getAuthor(client, post.authorId, "post")
                            }

                            PostWithAuthorAndComments(
                                post, commentsWithAuthor.await().awaitAll(), postAuthor.await()
                            ).also {
                                log("Пост ${it.post.id} получен полностью! Комментариев: ${it.comments.size}")
                            }
                        }
                    }.awaitAll()
                log(posts.joinToString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    Thread.sleep(30_000L)
}

suspend fun OkHttpClient.apiCall(url: String): Response {
    return suspendCoroutine { continuation ->
        Request.Builder()
            .url(url)
            .build()
            .let(::newCall)
            .enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }

                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }
            })
    }
}

suspend fun <T> makeRequest(url: String, client: OkHttpClient, typeToken: TypeToken<T>): T =
    withContext(Dispatchers.IO) {
        client.apiCall(url)
            .let { response ->
                if (!response.isSuccessful) {
                    response.close()
                    throw RuntimeException(response.message)
                }
                val body = response.body ?: throw RuntimeException("response body is null")
                gson.fromJson(body.string(), typeToken.type)
            }
    }

suspend fun getPosts(client: OkHttpClient): List<Post> {
    log("Posts START")
    return makeRequest("$BASE_URL/api/slow/posts", client, object : TypeToken<List<Post>>() {})
        .also {
            log("Posts END, count: ${it.size}")
        }
}

suspend fun getComments(client: OkHttpClient, id: Long): List<Comment> {
    log("Comment $id START")
    return makeRequest(
        "$BASE_URL/api/slow/posts/$id/comments",
        client,
        object : TypeToken<List<Comment>>() {})
        .also {
            log("Comment $id END, count: ${if (it.size > 0) it.size else "НЕТ"}")
        }
}

suspend fun getAuthor(client: OkHttpClient, id: Long, who: String): Author {
    log("Author $id START ($who)")
    return makeRequest("$BASE_URL/api/slow/authors/$id", client, object : TypeToken<Author>() {})
        .also {
            log("Author $id END ($who)")
        }
}

fun log(text: String) {
    val date: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    println("$date $text")
}