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
  Invoke-RestMethod -Method Post -Uri "http://localhost:8080/replays/from-beginning?maxRecords=100"

  curl:
  curl -X POST "http://localhost:8080/replays/from-beginning?maxRecords=100"

- Replay job listesini alma:
  curl "http://localhost:8080/replays"

API'ler

- POST /replays/from-beginning?maxRecords={optional}
  - Başlatılan replay için `ReplayJobStatus` döner (içinde `replayGroupId` bulunur).
- GET /replays
  - Mevcut replay işlerinin snapshot listesini döner.

Neden IDE (IntelliJ) `ReplayJobState` / `ReplayJobStatus` importlarını bulamıyor olabilir?

Eğer IDE sınıfların dosya sisteminde var olduğunu görüyorsa ama importları kırmızı gösteriyorsa ("cannot resolve symbol" vb.), yaygın sebepler:

1. Java sürümü / language level uyumsuzluğu:
   - `ReplayJobStatus` bir Java `record` olarak tanımlanmış (record'lar Java 16+ ile tanıtıldı; modern projelerde Java 17+ veya daha yeni bir JDK kullanılıyor). Projenin `pom.xml` içinde `<java.version>21</java.version>` olarak gözüküyor. IntelliJ'de proje SDK ve module language level'ın bu sürüme uygun olduğundan emin olun.

2. Maven multi-module projesi IDE'ye tam import edilmemiş olabilir:
   - Root `pom.xml`'i (aggregator) IntelliJ'e import edin veya Maven tool window'dan "Reimport" yapın.

3. `src/main/java` klasörü kaynak kökü (source root) olarak işaretlenmemiş olabilir.

4. IntelliJ cache / index hatası:
   - Caches'te bozulma olmuş olabilir; "Invalidate Caches / Restart" işe yarayabilir.

5. OneDrive gibi senkronize klasörlerde dosya kilitlenmesi veya path sorunları:
   - Bazen OneDrive bulut senkronizasyonu IDE ile çakışır. Gerekirse projeyi OneDrive dışına taşıyıp tekrar import edin.

Adım adım IntelliJ düzeltme önerisi

1. IntelliJ → File → Project Structure → Project: Project SDK ve Project language level'ı `17` veya `21` (pom'daki `<java.version>`) olarak ayarlayın.
2. IntelliJ → View → Tool Windows → Maven → projeyi seçip *Reload All Maven Projects* (yeniden import) yapın.
3. File → Project Structure → Modules → `eventConsumer` modülünü seçin. `src/main/java` klasörünün Sources (mavi) olarak işaretli olduğundan emin olun.
4. Terminal'den temiz ve derleme yapın:
   .\eventConsumer\mvnw clean package -DskipTests
   Derleme başarılı ise `eventConsumer/target/classes/com/rateLimitExample/eventConsumer/model/ReplayJobStatus.class` dosyası oluşmuş olmalıdır.
5. Eğer hala sorun varsa: File → Invalidate Caches / Restart → Invalidate and Restart.
6. Eğer diğer modüller (`eventProducer`) consumer model tiplerini doğrudan import etmeye çalışıyorsa, bu kötü bir bağımlılıktır. Ortak tipleri `common` adlı yeni bir modulde toplayıp her iki modüle bağımlılık olarak eklemeyi düşünün.

Kısa notlar / tavsiyeler

- Proje `record` kullanıyor; IDEA ve Project SDK >= 17 olmalı.
- Maven build başarılı ise derleyici (javac) kodu görebiliyor demektir; IDE tarafı ise index/caches ya da import/SDK sorununa işaret eder.
- Eğer producer modülü consumer model sınıflarını doğrudan kullanıyorsa, shared tipleri ayırmak daha temiz bir mimaridir.

Eğer isterseniz ben `rateLimitExample/README.md` dosyasını doğrudan repoya ekledim (bu dosya burada). Ayrıca isterseniz IntelliJ üzerinde adım adım tam olarak hangi ayarı değiştirmeniz gerektiğini ekran görüntüsüyle veya daha ayrıntılı bir rehberle sağlayabilirim.

---

Hazır olduğunuzda şu adımlardan birini seçin:
- README'yi değiştirmemi veya ek açıklama/örnek log/request-response eklememi istiyor musunuz?
- Şu anki IDE hatasını çözmek için sizin yerinize IntelliJ proje ayarlarında değişiklik yapamam ama adım adım sizi yönlendirebilirim; hangi IDE sürümünü kullanıyorsunuz (IntelliJ IDEA Community/Ultimate ve sürümü)?
