package http.utils

import java.io.{ File, FileInputStream, InputStream }
import java.security.{ KeyStore, SecureRandom }
import javax.net.ssl.{ KeyManagerFactory, SSLContext, TrustManagerFactory }

import akka.http.scaladsl.{ ConnectionContext, HttpsConnectionContext }

object HttpsContext {

  // Key Store
  val keyStore: KeyStore = KeyStore.getInstance("PKCS12")
  val keyStoreFile: InputStream = new FileInputStream(
    new File("src/main/resources/keystore.pkcs12")
  )
  val password = "akka-https".toCharArray // fetch it from a secure place
  keyStore.load(keyStoreFile, password)

  // Key Manager
  val keyManagerFactory = KeyManagerFactory.getInstance("SunX509") // PKI: Public Key Infrastructure
  keyManagerFactory.init(keyStore, password)

  // Trust Manager
  val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
  trustManagerFactory.init(keyStore)

  // SSL context
  val sslContext: SSLContext = SSLContext.getInstance("TLS")
  sslContext.init(
    keyManagerFactory.getKeyManagers,
    trustManagerFactory.getTrustManagers,
    new SecureRandom
  )

  val httpsConnectionContext: HttpsConnectionContext = ConnectionContext.httpsServer(sslContext)
}
