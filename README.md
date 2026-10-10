# Cloud Task Scheduling Simulation (CloudSim Plus 7.3.0)
> **Simulasi & Perbandingan Kinerja Algoritma Min-Min vs Cost-Based Greedy dengan Penempatan VM First-Fit pada Lingkungan Cloud Heterogen**

Proyek ini dibangun menggunakan **CloudSim Plus 7.3.0** dan **Java 17** untuk mensimulasikan penjadwalan komputasi awan (*cloud task scheduling*) secara realistis dan mengevaluasi *trade-off* antara efisiensi waktu (*Makespan*) dan biaya (*Cost*).

---

## 📖 DAFTAR ISI
1. [Arsitektur Sistem (2 Tahap Terpisah)](#1-arsitektur-sistem-2-tahap-terpisah)
2. [Penjelasan Host Fisik (Server)](#2-penjelasan-host-fisik-server)
3. [Penjelasan Virtual Machine (VM)](#3-penjelasan-virtual-machine-vm)
4. [Konsep & Hasil Penempatan VM ke Host (First-Fit)](#4-konsep--hasil-penempatan-vm-ke-host-first-fit)
5. [Alasan Pengujian Workload 100, 500, dan 1.000 Task](#5-alasan-pengujian-workload-100-500-dan-1000-task)
6. [Algoritma Penjadwalan: Min-Min vs Cost-Based Greedy](#6-algoritma-penjadwalan-min-min-vs-cost-based-greedy)
7. [Hasil Eksperimen & Analisis](#7-hasil-eksperimen--analisis)
8. [Peta Baris Kode (Code Reference)](#8-peta-baris-kode-code-reference)
9. [Cara Menjalankan Program](#9-cara-menjalankan-program)
10. [FAQ / Contekan Menjawab Pertanyaan Dosen](#10-faq--contekan-menjawab-pertanyaan-dosen)

---

## 1. Arsitektur Sistem (2 Tahap Terpisah)

Sistem ini memisahkan simulasi ke dalam **2 Tahap Berurutan** agar arsitektur infrastruktur dan algoritma penjadwalan tidak rancu:

```
[TAHAP 1: INFRASTRUKTUR / STATIS]
10 Host Fisik (57 Core) <=== [First-Fit] === 30 VM Heterogen (55 Core)
                                                     │
                                                     ▼
[TAHAP 2: PENJADWALAN / DINAMIS]
30 VM Aktif <=== [Min-Min / Cost-Greedy] === Task / Cloudlet (100, 500, 1.000)
```

1. **Tahap 1 — Penempatan VM ke Host (VM Placement):** 
   Bersifat **statis di awal** (tanpa *live migration*). Menempatkan 30 VM ke 10 Host menggunakan kebijakan **First-Fit Decreasing**.
2. **Tahap 2 — Penjadwalan Task ke VM (Task Scheduling):** 
   Setelah 30 VM aktif, ribuan task (Cloudlet) dijadwalkan ke VM menggunakan algoritma **Min-Min** dan **Cost-Based Greedy**.

---

## 2. Penjelasan Host Fisik (Server)

### Mengapa 10 Host Dibagi 3 Kelas?
Di datacenter komersial nyata (seperti AWS, Google Cloud, atau Azure), server fisik di rak tidak semuanya seragam. Ada server baru berspesifikasi tinggi, server standar, dan server lama/hemat. Kondisi ini disebut **Lingkungan Heterogen (*Heterogeneous Environment*)**.

### Mengapa Penomorannya "Host 0–2", "Host 3–6", dan "Host 7–9"?
Dalam bahasa pemrograman Java dan simulator CloudSim Plus, indeks array selalu dimulai dari **0**, bukan 1:
* **Host High-End (3 unit):** Indeks **Host 0, Host 1, Host 2** (Total 3 unit).
* **Host Mid-End (4 unit):** Indeks **Host 3, Host 4, Host 5, Host 6** (Total 4 unit).
* **Host Low-End (3 unit):** Indeks **Host 7, Host 8, Host 9** (Total 3 unit).
* **Total:** 3 + 4 + 3 = **10 Host (Host 0 s.d. Host 9)**.

| Kelas Host | ID Host | CPU / PE | MIPS/Core | RAM | Storage | Bandwidth | Vm Scheduler |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **High-end** | Host 0, 1, 2 | 8 Core | 3.000 | 32 GB | 1.000 GB | 10.000 Mbps | `VmSchedulerSpaceShared` |
| **Mid-end** | Host 3, 4, 5, 6 | 6 Core | 2.000 | 16 GB | 500 GB | 5.000 Mbps | `VmSchedulerSpaceShared` |
| **Low-end** | Host 7, 8, 9 | 3 Core | 1.000 | 8 GB | 250 GB | **2.000 Mbps** | `VmSchedulerSpaceShared` |
| **TOTAL** | **10 Host** | **57 PE** | - | **184 GB** | **5.750 GB** | - | *(Tanpa Overcommit CPU)* |

> **Catatan Penting V7:** Bandwidth Host Low-End diset ke **2.000 Mbps** agar mampu menampung 3 VM-Small (3 × 500 = 1.500 Mbps). Pada versi lama bernilai 1.000 Mbps yang menyebabkan VM ke-30 gagal ditempatkan.

---

## 3. Penjelasan Virtual Machine (VM)

Pengguna cloud menyewa mesin virtual dengan spesifikasi beragam sesuai kebutuhan komputasi:
* **VM-Large (5 unit):** 4 Core CPU, 8 GB RAM, 2.500 MIPS (untuk tugas berat).
* **VM-Medium (10 unit):** 2 Core CPU, 4 GB RAM, 1.500 MIPS (untuk tugas standar).
* **VM-Small (15 unit):** 1 Core CPU, 2 GB RAM, 1.000 MIPS (untuk tugas ringan).

### Perhitungan Kapasitas Fisik vs Kebutuhan VM:
* **Daya Tampung Host Fisik:** $(3 \times 8) + (4 \times 6) + (3 \times 3) = 24 + 24 + 9 = \mathbf{57\text{ Core}}$.
* **Kebutuhan 30 VM:** $(5 \times 4) + (10 \times 2) + (15 \times 1) = 20 + 20 + 15 = \mathbf{55\text{ Core}}$.
* **Kesimpulan:** Seluruh 30 VM **pasti tertampung** dengan aman dan menyisakan **2 Core cadangan** di Host 9.

| Tipe VM | Jumlah | vCPU (PE) | MIPS | RAM | Storage | Bandwidth | Tarif Komputasi ($C_{MI}$) | Tarif Transfer ($C_{KB}$) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **VM-Small** | 15 | 1 | 1.000 | 2 GB | 20 GB | 500 Mbps | 0.00010 | 0.00005 |
| **VM-Medium** | 10 | 2 | 1.500 | 4 GB | 40 GB | 1.000 Mbps | 0.00013 | 0.00004 |
| **VM-Large** | 5 | 4 | 2.500 | 8 GB | 80 GB | 2.000 Mbps | 0.00020 | 0.00003 |
| **TOTAL** | **30 VM** | **55 PE** | - | **85 GB** | **1.100 GB** | - | - | - |

---

## 4. Konsep & Hasil Penempatan VM ke Host (First-Fit)

### Cara Kerja Algoritma First-Fit Decreasing:
1. Daftar VM diurutkan dari yang paling besar ke kecil (**Large $\rightarrow$ Medium $\rightarrow$ Small**). Hal ini krusial agar core pada Host tidak terfragmentasi oleh VM kecil di awal.
2. Setiap VM mencari Host pertama mulai dari **Host 0** yang memiliki sisa kapasitas CPU, RAM, storage, dan bandwidth yang mencukupi.
3. Begitu Host pertama ditemukan, VM langsung dipasang di sana.

### Hasil Penempatan Riil 30 VM pada 10 Host:
```
Host     Kelas  Kapasitas PE   VM Terpasang   PE Terpakai  Rincian VM
-----------------------------------------------------------------------------------------
Host 0   High            8 PE           2 VM          8 PE  2x Large
Host 1   High            8 PE           2 VM          8 PE  2x Large
Host 2   High            8 PE           3 VM          8 PE  1x Large, 2x Medium
Host 3   Mid             6 PE           3 VM          6 PE  3x Medium
Host 4   Mid             6 PE           3 VM          6 PE  3x Medium
Host 5   Mid             6 PE           4 VM          6 PE  2x Medium, 2x Small
Host 6   Mid             6 PE           6 VM          6 PE  6x Small
Host 7   Low             3 PE           3 VM          3 PE  3x Small
Host 8   Low             3 PE           3 VM          3 PE  3x Small
Host 9   Low             3 PE           1 VM          1 PE  1x Small (Sisa 2 PE Kosong)
-----------------------------------------------------------------------------------------
TOTAL   : 10 Host, 57 PE Kapasitas | 30 VM Terpasang, 55 PE Terpakai (Sisa 2 PE)
STATUS  : Semua 30 VM berhasil ditempatkan secara statis (First-Fit OK).
```

---

## 5. Alasan Pengujian Workload 100, 500, dan 1.000 Task

Pengujian dilakukan pada 3 skenario jumlah task untuk melakukan **Uji Skalabilitas (*Scalability & Stress Testing*)**:

| Jumlah Task | Rasio per VM | Kategori Beban | Tujuan Pengujian |
| :---: | :---: | :---: | :--- |
| **100 Task** | $\approx$ **3 task/VM** | **Beban Ringan (*Baseline*)** | Memvalidasi kebenaran logika algoritma saat server masih longgar dan tanpa antrean berarti. |
| **500 Task** | $\approx$ **16 task/VM** | **Beban Sedang** | Mengamati awal mula terbentuknya antrean (*queuing delay*) pada VM. |
| **1.000 Task** | $\approx$ **33 task/VM** | **Beban Tinggi (*Stress Test*)** | Menguji ketahanan algoritma saat server sangat padat. Di titik ini kelemahan dan kelebihan asli algoritma terlihat paling ekstrem. |

---

## 6. Algoritma Penjadwalan: Min-Min vs Cost-Based Greedy

### 1. Min-Min (Fokus Minimasi Waktu / Makespan)
* **Konsep:**
  1. Untuk setiap task yang belum selesai, hitung waktu penyelesaian (*Completion Time* / $CT$) pada seluruh VM yang *feasible* ($vCPU_{VM} \ge PE_{task}$).
  2. Cari task yang memiliki nilai completion time minimum secara global (*minimum of minimum*).
  3. Jadwalkan task tersebut ke VM pilihannya dan perbarui *ReadyTime* VM tersebut.
* **Karakteristik:** Sangat kencang, memaksimalkan throughput, tetapi mengabaikan biaya (*cost-blind*).

### 2. Cost-Based Greedy (Fokus Minimasi Biaya / Cost)
* **Konsep:**
  1. Hitung total biaya komputasi dan transfer data:
     $$\text{Cost} = (\text{length} \times C_{MI}) + ((\text{file\_size} + \text{output\_size}) \times C_{KB})$$
  2. Pasangkan task ke VM yang menghasilkan biaya paling murah.
  3. **Tie-Breaker:** Jika ada beberapa VM dengan tarif sama (misal sesama VM-Small), pilih VM yang memiliki waktu antrean (*ready time*) terkecil agar tidak menumpuk di 1 VM saja.
* **Karakteristik:** Sangat hemat biaya, tetapi membuat antrean menumpuk parah di VM murah.

---

## 7. Hasil Eksperimen & Analisis

Hasil eksekusi batch dataset 1.000 task sintetis (*Seed = 42*):

| Algoritma | Task | Makespan (s) | Cost | AvgWait (s) | Util (%) | DI | Throughput (task/s) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Min-Min** | 100 | **163.41** | 826.41 | **13.35** | 55.55% | 0.663 | **0.6120** |
| CostGreedy | 100 | 342.54 | **613.30** | 91.28 | 50.63% | 0.843 | 0.2919 |
| **Min-Min** | 500 | **625.00** | 3.805.21 | **125.61** | 75.29% | 0.677 | **0.8000** |
| CostGreedy | 500 | 1.436.88 | **2.914.46** | 565.73 | 56.76% | 0.759 | 0.3480 |
| **Min-Min** | 1.000 | **1.253.30** | 7.455.47 | **263.41** | 74.25% | 0.704 | **0.7979** |
| CostGreedy | 1.000 | 2.865.71 | **5.705.26** | 1.186.07 | 56.45% | 0.783 | 0.3490 |

### Analisis Kritis:
1. **Makespan & Throughput:** Min-Min **2,3x lebih cepat** daripada CostGreedy pada 1.000 task (1.253s vs 2.865s) dengan throughput mencapai ~0.80 task/s.
2. **Biaya (Cost):** CostGreedy berhasil menghemat biaya hingga **~23,5% lebih murah** (5.705 vs 7.455 unit biaya).
3. **Waktu Tunggu (AvgWait):** Penghematan biaya CostGreedy memicu waktu antre yang membengkak hingga **1.186 detik (~19,7 menit)** karena task berebut VM murah, sementara pada Min-Min hanya 263 detik.

---

## 8. Peta Baris Kode (Code Reference)

Semua implementasi logika berada pada file: `src/main/java/sim/TaskSchedulingSim.java`

| Komponen | Baris Kode | Deskripsi |
| :--- | :---: | :--- |
| **Spesifikasi Host** | `Baris 42–50` | Konstanta jumlah host, core, MIPS, RAM, storage, dan bandwidth. |
| **Spesifikasi VM** | `Baris 51–59` | Konstanta jumlah VM, vCPU, MIPS, RAM, dan bandwidth. |
| **Pembuatan Host** | `Baris 103–116` | Method `createHosts()` dengan `VmSchedulerSpaceShared`. |
| **Pembuatan VM** | `Baris 118–136` | Method `createVms()` dengan urutan Large $\rightarrow$ Medium $\rightarrow$ Small. |
| **First-Fit Policy** | `Baris 141, 320` | `new VmAllocationPolicyFirstFit()` dari CloudSim Plus. |
| **Laporan Penempatan** | `Baris 138–262` | Method `reportVmPlacement()` mencetak tabel alokasi VM ke Host. |
| **Pengecekan Feasible** | `Baris 380–382` | Memastikan $PE_{VM} \ge PE_{task}$ sebelum dijadwalkan. |
| **Algoritma Min-Min** | `Baris 385–413` | Method `minMin()` (2-phase minimum completion time). |
| **Cost-Based Greedy** | `Baris 416–438` | Method `costGreedy()` dengan mekanisme tie-breaker ready time. |
| **Hitung Metrik** | `Baris 441–520` | Method `computeMetrics()` menghitung Makespan, Cost, AvgWait, Util, DI, Thr. |

---

## 9. Cara Menjalankan Program

### Prasyarat
* Java JDK 17
* Apache Maven 3.8+

### Perintah Eksekusi
Buka terminal di root direktori proyek, jalankan:
```powershell
mvn -q exec:java "-Dexec.args=batch dataset_synthetic_1000.csv"
```
Output tabel akan langsung muncul di terminal dan otomatis tersimpan ke file `hasil_batch.csv` serta `hasil_vm_placement.txt`.

---

## 10. FAQ / Contekan Menjawab Pertanyaan Dosen

**Q1: "Kenapa Host 9 menyisakan 2 PE menganggur?"**
> *"Karena kapasitas 10 Host fisik kami adalah 57 PE, sedangkan 30 VM hanya membutuhkan 55 PE (5×4 + 10×2 + 15×1 = 55 PE). Jadi 2 PE sisa di Host 9 adalah cadangan kapasitas yang wajar dan bukan error."*

**Q2: "Kenapa pembuatan VM diurutkan Large $\rightarrow$ Medium $\rightarrow$ Small?"**
> *"Untuk menerapkan konsep First-Fit Decreasing. Jika VM kecil ditaruh duluan, core pada Host High-End akan terpecah (fragmentasi), sehingga VM Large yang butuh 4 PE utuh bisa gagal mendapatkan tempat."*

**Q3: "Apa bedanya First-Fit dengan Min-Min di programmu?"**
> *"Keduanya berada di level yang berbeda:*
> - *First-Fit berada di **Tahap 1 (Infrastruktur)**: Menempatkan **VM ke Host** secara statis sebelum simulasi berjalan.*
> - *Min-Min berada di **Tahap 2 (Scheduling)**: Menjadwalkan **Task (Cloudlet) ke VM** yang sudah menyala selama simulasi berjalan."*

**Q4: "Kenapa menguji di 100, 500, dan 1.000 task?"**
> *"Untuk uji skalabilitas (Scalability Testing). 100 task sebagai baseline beban ringan (3 task/VM), 500 task untuk beban sedang (16 task/VM), dan 1.000 task sebagai stress-test beban tinggi (33 task/VM) guna membuktikan perilaku kestabilan dan kemacetan antrean pada kedua algoritma."*


**LINk GOOGLE DOCUMENTS: https://docs.google.com/document/d/14FItXP_HITYkznPutjx_mhvHB2T92whwGRFqltupONA/edit?tab=t.0**
