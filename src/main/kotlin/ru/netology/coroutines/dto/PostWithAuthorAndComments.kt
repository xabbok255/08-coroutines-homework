package ru.netology.coroutines.dto

data class PostWithAuthorAndComments(
    val post: Post,
    val comments: List<CommentWithAuthor>,
    val author: Author
)
