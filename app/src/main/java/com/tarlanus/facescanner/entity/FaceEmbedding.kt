package com.tarlanus.facescanner.entity

data class FaceEmbedding(
     val userId: String,
     val embedding: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FaceEmbedding

        if (userId != other.userId) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = userId.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}