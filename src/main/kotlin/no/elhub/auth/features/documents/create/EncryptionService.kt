package no.elhub.auth.features.documents.create

import arrow.core.Either
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.decodeBase64String
import io.ktor.util.encodeBase64
import kotlinx.io.bytestring.decodeToByteString
import kotlinx.io.bytestring.encode
import kotlinx.io.bytestring.encodeToByteString
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PipedInputStream
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

interface EncryptionService {
    suspend fun encrypt(inputStream: InputStream): Either<EncryptionError, CipherInputStream>
    suspend fun decrypt(inputStream: InputStream): Either<EncryptionError, CipherInputStream>
}

sealed class EncryptionError {
    data object UnexpectedError : EncryptionError()
}

private const val DEFAULT_ALGORITHM = "AES"
private const val DEFAULT_KEY_SIZE = 256
private const val DEFAULT_TRANSFORMATION = "AES/CBC/PKCS7PADDING"

data class JavaEncryptionConfig(
    val algorithm: String = DEFAULT_ALGORITHM,
    val keySize: Int = DEFAULT_KEY_SIZE,
    val transformation: String = DEFAULT_TRANSFORMATION,
    val key: String,
    val salt: String,
)

class JavaEncryptionService(
    val cfg: JavaEncryptionConfig,
) : EncryptionService {
    override suspend fun encrypt(inputStream: InputStream): Either<EncryptionError, CipherInputStream> =
        Either.catch {
            val secretKey = generateSecretKey()
            val iv = generateInitializationVector()
            encryptPriv(inputStream, secretKey, iv)
        }.mapLeft { EncryptionError.UnexpectedError }

    override suspend fun decrypt(inputStream: InputStream): Either<EncryptionError, CipherInputStream> =
        Either.catch {
            // TODO: Retrieve the key and IV from storage
            val secretKey = generateSecretKey()
            val iv = generateInitializationVector()
            decryptPriv(inputStream, secretKey, iv)
        }.mapLeft { EncryptionError.UnexpectedError }

    private fun generateSecretKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(cfg.algorithm)
        keyGenerator.init(cfg.keySize)
        return keyGenerator.generateKey()
    }

    private fun generateInitializationVector(): IvParameterSpec {
        val iv = ByteArray(16)
        val secureRandom = SecureRandom()
        secureRandom.nextBytes(iv)
        return IvParameterSpec(iv)
    }

    private fun encryptPriv(toEncrypt: InputStream, secretKey: SecretKey, iv: IvParameterSpec): CipherInputStream {
        val cipher =
            Cipher
                .getInstance(cfg.transformation)
                .apply {
                    init(Cipher.ENCRYPT_MODE, secretKey, iv)
                }

        return CipherInputStream(toEncrypt, cipher)
    }

    private fun decryptPriv(
        toDecrypt: InputStream,
        secretKey: SecretKey,
        iv: IvParameterSpec
    ): CipherInputStream {
        val cipher =
            Cipher
                .getInstance(cfg.transformation)
                .apply {
                    init(Cipher.DECRYPT_MODE, secretKey, iv)
                }
        return CipherInputStream(toDecrypt, cipher)
    }

}
