package com.example.haiyangapp.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 知识块实体
 * 存储文档分块后的文本内容和向量嵌入
 */
@Entity(
    tableName = "knowledge_chunks",
    foreignKeys = [
        ForeignKey(
            entity = KnowledgeDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE  // 删除文档时级联删除所有分块
        )
    ],
    indices = [Index("documentId")]
)
data class KnowledgeChunkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 所属文档ID */
    val documentId: Long,

    /** 文本内容 */
    val content: String,

    /** 块在文档中的索引位置 */
    val chunkIndex: Int,

    /**
     * 向量嵌入 (Float32数组序列化为ByteArray)
     * 使用 all-MiniLM-L6-v2 模型，维度为 384
     */
    val embedding: ByteArray,

    /** 文本的token数量（用于上下文长度控制） */
    val tokenCount: Int = 0,

    /** 额外元数据（JSON格式，可存储页码等信息） */
    val metadata: String? = null
) {
    companion object {
        /** 嵌入向量维度 (all-MiniLM-L6-v2) */
        const val EMBEDDING_DIMENSION = 384

        /**
         * 将FloatArray转换为ByteArray
         */
        fun floatArrayToByteArray(floats: FloatArray): ByteArray {
            val bytes = ByteArray(floats.size * 4)
            for (i in floats.indices) {
                val bits = java.lang.Float.floatToIntBits(floats[i])
                bytes[i * 4] = (bits shr 24).toByte()
                bytes[i * 4 + 1] = (bits shr 16).toByte()
                bytes[i * 4 + 2] = (bits shr 8).toByte()
                bytes[i * 4 + 3] = bits.toByte()
            }
            return bytes
        }

        /**
         * 将ByteArray转换为FloatArray
         */
        fun byteArrayToFloatArray(bytes: ByteArray): FloatArray {
            val floats = FloatArray(bytes.size / 4)
            for (i in floats.indices) {
                val bits = (bytes[i * 4].toInt() and 0xFF shl 24) or
                        (bytes[i * 4 + 1].toInt() and 0xFF shl 16) or
                        (bytes[i * 4 + 2].toInt() and 0xFF shl 8) or
                        (bytes[i * 4 + 3].toInt() and 0xFF)
                floats[i] = java.lang.Float.intBitsToFloat(bits)
            }
            return floats
        }
    }

    /**
     * 获取嵌入向量的FloatArray形式
     */
    fun getEmbeddingAsFloatArray(): FloatArray {
        return byteArrayToFloatArray(embedding)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as KnowledgeChunkEntity
        if (id != other.id) return false
        if (documentId != other.documentId) return false
        if (chunkIndex != other.chunkIndex) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + documentId.hashCode()
        result = 31 * result + chunkIndex
        return result
    }
}
