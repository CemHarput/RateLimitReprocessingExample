# rateLimitExample — Kafka Replay / Rate-limit Study Case

Bu proje, Spring Boot kullanarak Apache Kafka üzerinde "rate limit" ve "reprocessing / replay" senaryosunu gösteren bir study case'tir. Proje iki modülden oluşur:

- `eventProducer`: Örnek mesajlar üreten küçük bir producer uygulaması.
- `eventConsumer`: Mesajları tüketen, işleyen ve ihtiyaç durumunda tekrar (replay) ederek veri kaybı telafisini simüle eden consumer uygulaması.

Özet

`eventConsumer` içinde replay mekanizması ayrı bir tüketici (consumer) kullanılarak uygulanır. Yöntem offset geri sarma (production consumer offset'ini reset etme) değildir; bunun yerine her replay işi için yeni bir consumer grubuyla (unique group id) `seekToBeginning` çağrısı yapılır ve mesajlar en baştan yeniden okunur. Bu, üretim (normal) consumer gruplarının offset durumunu değiştirmez.

Neden bu yöntem?

- Production tüketici offsetlerini değiştirmek risklidir (duplicate processing ya da veri kaybı riskleri).
- Ayrı bir replay consumer, replay sürecini izole eder ve izlenebilirlik sağlar (her replay bir `replayGroupId` ile takip edilir).

Proje yapısı (özet)

- `pom.xml` — root aggregator POM (multi-module).
- `eventProducer/` — örnek event üretici.
  - `src/main/java/com/rateLimitExample/eventProducer/service/OrderProducerService.java` — örnek event üretimi.
- `eventConsumer/` — consumer ve replay altyapısı.
  - `src/main/java/com/rateLimitExample/eventConsumer/service/ReplayService.java` — replay akışının çekirdeği (yeni consumer oluşturma, subscribe, assignment bekleme, `seekToBeginning`, poll ve işleme).
  - `src/main/java/com/rateLimitExample/eventConsumer/model/ReplayJobState.java` — replay işinin mutable durumu (progress vs status).
  - `src/main/java/com/rateLimitExample/eventConsumer/model/ReplayJobStatus.java` — replay durumunun immutable snapshot'ı (Java record).
  - `src/main/java/com/rateLimitExample/eventConsumer/controller/ReplayController.java` — replay başlatma ve job listeleme için REST endpoint'leri.
  - `src/main/resources/application.properties` — Kafka konfigürasyonları (topic, bootstrap servers vs.).

Nasıl çalışıyor? (teknik özet)

1. API üzerinden POST `/replays/from-beginning?maxRecords={optional}` çağrısı ile bir replay başlatılır.
2. `ReplayService` yeni bir `replayGroupId` üretir ve `ReplayJobState` nesnesi oluşturur.
3. Replay için ayrı bir `KafkaConsumer` (unique group id) yaratılır.
4. Consumer topic'e subscribe olur, partition assignment gelene kadar `poll` ile bekler.
5. Assignment alındıktan sonra `consumer.seekToBeginning(consumer.assignment())` çağrısı ile tüm partition'ların başlangıç offset'lerine gidilir.
6. Consumer döngüsel `poll` ile kayıtları okur, her kaydı `OrderProcessingService.process(...)` gibi iç servise iletir ve `ReplayJobState` üzerinde progress güncellenir.
7. Mesajlar işlendiğinde job tamamlanır ya da hata durumunda hata kaydı tutulur.

Bu yaklaşım offset geri sarmayı (production group'a müdahale) içermez; replay tamamen ayrı bir tüketici üzerinden yapılır.

Build & Run (Windows PowerShell)

- Tüm projeyi build:
  .\mvnw -DskipTests package

- Sadece consumer modülünü build:
  .\eventConsumer\mvnw -DskipTests package

- Consumer uygulamasını çalıştırma (Spring Boot):
  .\eventConsumer\mvnw spring-boot:run

- Replay tetikleme (örnek):
  PowerShell:
  Invoke-RestMethod -Method Post -Uri "http://localhost:8082/replays/from-beginning?maxRecords=100"

  curl:
  curl -X POST "http://localhost:8082/replays/from-beginning?maxRecords=100"

- Replay job listesini alma:
  curl "http://localhost:8082/replays"

API'ler

- POST /replays/from-beginning?maxRecords={optional}
  - Başlatılan replay için `ReplayJobStatus` döner (içinde `replayGroupId` bulunur).
- GET /replays
  - Mevcut replay işlerinin snapshot listesini döner.




