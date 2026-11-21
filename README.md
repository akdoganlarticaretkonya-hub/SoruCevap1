# Stok Cevap

WhatsApp / WhatsApp Business'a gelen mesaj, Google Sheets tablosundaki 1. sütundaki
metinle (stok adı) birebir aynıysa, 2. sütundaki değeri (stok mevcudu) cevap olarak gönderir.
Başka hiçbir şey yapmaz.

## APK'yı alma (bilgisayara hiçbir şey kurmadan)

1. github.com'da ücretsiz hesap açın, **New repository** ile (Private olabilir) boş bir depo oluşturun.
2. Depoda **Add file → Upload files**. Bu klasörün İÇİNDEKİ her şeyi (gizli `.github` klasörü dahil)
   sürükleyip bırakın, **Commit changes**.
   - `.github` klasörü yüklenmezse: **Add file → Create new file**, adına
     `.github/workflows/build.yml` yazın ve bu klasördeki `build.yml` içeriğini yapıştırın.
3. Üstte **Actions** sekmesi → "APK derle" çalışır (yaklaşık 3-5 dk). Yeşil tik olunca
   çalışmanın içinde en altta **StokCevap-APK** dosyasını indirin, zip'ten `app-debug.apk` çıkar.
4. APK'yı telefona gönderip kurun (Android "bilinmeyen kaynaklardan kuruluma izin ver" isteyebilir).

## Telefonda ayar

1. Uygulamayı açın, Google Sheets bağlantısını yapıştırın (tablo: **Bağlantıya sahip herkes görüntüleyebilir**).
   Sayfa adı varsayılan `Sayfa1`.
2. İsterseniz "eşleşme yoksa" cevabını yazın. Boş bırakırsanız eşleşmeyen mesaja cevap verilmez.
3. **Kaydet ve tabloyu şimdi yenile**, sonra **Dene** kutusuyla bir stok adı yazıp cevabı görün.
4. **1) Bildirim erişimini aç** → Stok Cevap'ı açın. **2) Pil kısıtlamasını kaldır** → izin verin.

## Bilmeniz gerekenler

- Cevap, WhatsApp'ın bildirimindeki "Yanıtla" özelliğiyle gider. Bu yüzden:
  - O sohbet WhatsApp'ta açıkken bildirim gelmez, cevap da gitmez.
  - **Sessize alınmış** sohbetler/gruplar bildirim üretmez, cevap verilmez.
  - WhatsApp bildirimleri açık olmalı; ayarlarda "bildirimde mesaj içeriğini gizle" kapalı olmalı.
- Tablo en fazla 15 dakika eski veriyle çalışır (eskiyse mesaj gelince yeniler; internet yoksa son veriyi kullanır).
- Eşleşme: büyük/küçük harf, baştaki/sondaki ve fazla boşluklar, I/İ/ı farkı yok sayılır; metin aynı olmalıdır.
- Mesaj 2 dakikadan eskiyse (telefon kapalıyken gelmişse) cevaplanmaz.
- Eşleşme yok cevabı aynı sohbete 30 dakikada en fazla bir kez gider. Gruplarda varsayılan olarak hiç gitmez.
- Otomatik cevap araçları WhatsApp kurallarında gri alandır; düşük hacimde genelde sorun çıkmaz ama hesap kısıtlama riski sıfır değildir.
