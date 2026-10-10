# SCRIPT PRESENTASI + DEMO 1 (REVISI FINAL)
## Cloud Task Scheduling: Min-Min vs Cost-Based Greedy (CloudSim Plus 7.3.0)

---

### 0. PEMBUKA (Sebelum Share Screen)
“Selamat pagi/siang, Pak/Bu. Pada Demo 1 ini kami mempresentasikan implementasi simulasi Cloud Task Scheduling menggunakan CloudSim Plus 7.3.0. Menjawab masukan dan revisi pada sesi sebelumnya, kami telah memperjelas arsitektur sistem ke dalam dua tahap yang terpisah dan berurutan secara tegas:
1. **Tahap 1:** Penempatan VM ke Host secara statis di awal menggunakan algoritma **First-Fit**.
2. **Tahap 2:** Penjadwalan Task (Cloudlet) ke VM yang sudah aktif menggunakan algoritma **Min-Min** dan **Cost-Based Greedy** sebagai pembanding.”

---

### 1. TAHAP 1: ARSITEKTUR & PENEMPATAN VM KE HOST (FIRST-FIT)
“Pertama, kami jelaskan lingkungan cloud dan pemetaan VM ke Host.
Datacenter kami memiliki **10 Host heterogen** (Total 57 PE):
- **3 Host High-End:** @ 8 PE, 32 GB RAM, 10.000 Mbps BW
- **4 Host Mid-End:** @ 6 PE, 16 GB RAM, 5.000 Mbps BW
- **3 Host Low-End:** @ 3 PE, 8 GB RAM, 2.000 Mbps BW

Kebutuhan VM yang dideploy adalah **30 VM heterogen** (Total 55 PE):
- **5 VM Large:** @ 4 PE, 8 GB RAM
- **10 VM Medium:** @ 2 PE, 4 GB RAM
- **15 VM Small:** @ 1 PE, 2 GB RAM

**Bagaimana First-Fit bekerja pada penempatan VM?**
Pemetaan ini dilakukan sekali di awal dan bersifat **statis** (tanpa live migration). VM diurutkan dari yang terbesar (Large → Medium → Small) agar kapasitas core tidak terfragmentasi. Algoritma First-Fit menelusuri Host dari indeks 0 dan menempatkan VM pada Host pertama yang sumber dayanya mencukupi:
- **Host 0 (High, 8 PE):** menampung **2x Large** (8 PE terpakai, penuh)
- **Host 1 (High, 8 PE):** menampung **2x Large** (8 PE terpakai, penuh)
- **Host 2 (High, 8 PE):** menampung **1x Large + 2x Medium** (8 PE terpakai, penuh)
- **Host 3 & 4 (Mid, @6 PE):** masing-masing menampung **3x Medium** (6 PE terpakai, penuh)
- **Host 5 (Mid, 6 PE):** menampung **2x Medium + 2x Small** (6 PE terpakai, penuh)
- **Host 6 (Mid, 6 PE):** menampung **6x Small** (6 PE terpakai, penuh)
- **Host 7 & 8 (Low, @3 PE):** masing-masing menampung **3x Small** (3 PE terpakai, penuh)
- **Host 9 (Low, 3 PE):** menampung **1x Small** (1 PE terpakai, sisa 2 PE menganggur)

Dengan ini, seluruh 30 VM teralokasi sempurna tanpa kegagalan kapasitas CPU, RAM, bandwidth, maupun storage.”

---

### 2. TAHAP 2: PENJADWALAN TASK KE VM (MIN-MIN VS COST-BASED GREEDY)
“Setelah 30 VM terpasang pada Host, simulasi menjalankan Tahap 2, yaitu penjadwalan task:
- **Min-Min:** Menghitung waktu penyelesaian (completion time) untuk semua task pada VM yang feasible (vCPU VM ≥ PE task). Memilih task dengan minimum completion time secara global di setiap iterasi untuk meminimalkan Makespan.
- **Cost-Based Greedy:** Memprioritaskan penghematan biaya dengan memilih VM yang menghasilkan biaya komputasi dan transfer data paling murah untuk setiap task.

Workload yang diuji adalah 100, 500, dan 1.000 task pada skenario batch dataset.”

---

### 3. LIVE DEMO (Pindah ke Terminal)
[BUKA TERMINAL]

“Sekarang kami jalankan simulasinya langsung melalui terminal Maven.”

Perintah yang diketik:
```powershell
mvn -q exec:java "-Dexec.args=batch dataset_synthetic_1000.csv"
```

[TUNGGU SEBENTAR HINGGA OUTPUT MUNCUL]

---

### 4. ANALISIS HASIL
“Dapat kita lihat bersama pada terminal:
1. **Laporan First-Fit (di awal output):** Sistem mencetak tabel penempatan VM per host, membuktikan bahwa 30 VM terpasang lengkap di 10 host dengan 55 PE terpakai dan 2 PE cadangan pada Host 9.
2. **Laporan Perbandingan Algoritma:**
   - **Makespan:** Min-Min secara konsisten menghasilkan Makespan jauh lebih cepat daripada Cost-Based Greedy (contoh pada 1.000 task: 1.253 detik vs 2.865 detik, lebih dari 2x lebih cepat).
   - **Throughput:** Min-Min mencapai throughput lebih tinggi (~0.80 task/s vs ~0.35 task/s).
   - **Cost:** Sebaliknya, Cost-Based Greedy berhasil meminimalkan simulated cost lebih murah (misal 5.705 vs 7.455 pada 1.000 task) karena sengaja memprioritaskan VM murah meskipun harus mengorbankan waktu tunggu antrian (*AvgWait* lebih tinggi).”

---

### 5. PENUTUP
“Dengan demikian, implementasi ini telah memisahkan secara jelas penempatan VM ke Host melalui First-Fit statis, serta perbandingan penjadwalan task antara Min-Min dan Cost-Based Greedy. Seluruh hasil juga telah tersimpan secara otomatis di file `hasil_batch.csv`. 

Sekian presentasi dari kami, kami persilakan jika ada pertanyaan dari Bapak/Ibu dosen. Terima kasih.”

