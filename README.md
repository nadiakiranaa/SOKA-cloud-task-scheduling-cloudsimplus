# Cloud Task Scheduling Simulation (CloudSim Plus)
> **Perbandingan Kinerja Algoritma Min-Min vs Cost-Based Greedy dengan Penempatan VM First-Fit pada Lingkungan Cloud Heterogen**

Proyek simulasi ini dibangun menggunakan **CloudSim Plus 7.3.0** dan **Java 17** untuk mengevaluasi kinerja penjadwalan tugas (*task scheduling*) independen pada arsitektur cloud bertingkat (*heterogeneous resources*).

---

## 🏛️ Arsitektur Sistem (2 Tahap)

Simulasi ini memisahkan secara tegas dua tahap:

### Tahap 1: Penempatan VM ke Host (VM Placement — Statis)
Penempatan VM ke Host fisik dilakukan **sekali di awal** menggunakan algoritma **First-Fit** (`VmAllocationPolicyFirstFit`):
- **10 Host Heterogen (Total 57 PE):**
  - 3 Host High-End (8 PE, 32 GB RAM, 10.000 Mbps BW)
  - 4 Host Mid-End (6 PE, 16 GB RAM, 5.000 Mbps BW)
  - 3 Host Low-End (3 PE, 8 GB RAM, 2.000 Mbps BW)
- **30 VM Heterogen (Total 55 PE):**
  - 5 VM Large (4 PE)
  - 10 VM Medium (2 PE)
  - 15 VM Small (1 PE)

Hasil penempatan First-Fit:
- **Host 0 & 1:** 2x Large
- **Host 2:** 1x Large, 2x Medium
- **Host 3 & 4:** 3x Medium
- **Host 5:** 2x Medium, 2x Small
- **Host 6:** 6x Small
- **Host 7 & 8:** 3x Small
- **Host 9:** 1x Small *(sisa 2 PE)*

### Tahap 2: Penjadwalan Task ke VM (Task Scheduling — Dinamis)
Setelah topologi VM terkunci, ribuan task (Cloudlet) dijadwalkan ke VM yang aktif menggunakan dua algoritma pembanding:
1. **Min-Min:** Memprioritaskan minimasi *Makespan* dengan memilih task yang memiliki *minimum completion time* global.
2. **Cost-Based Greedy:** Memprioritaskan penghematan biaya komputasi dan transfer data.

---

## 🚀 Cara Menjalankan

### Persyaratan
- Java JDK 17
- Apache Maven 3.8+

### Kompilasi
```bash
mvn -q clean compile
```

### Eksekusi Simulasi
Di terminal PowerShell / Bash:
```powershell
mvn -q exec:java "-Dexec.args=batch dataset_synthetic_1000.csv"
```
*(Atau tanpa argumen untuk menjalankan synthetic workload)*

---

## 📊 Hasil Evaluasi (Synthetic Dataset — Seed 42)

| Algoritma | Task | Makespan (detik) | Cost | Avg Wait Time (detik) | Throughput (task/s) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Min-Min** | 100 | **163.41** | 826.41 | **13.35** | **0.6120** |
| CostGreedy | 100 | 342.54 | **613.30** | 91.28 | 0.2919 |
| **Min-Min** | 500 | **625.00** | 3805.21 | **125.61** | **0.8000** |
| CostGreedy | 500 | 1436.88 | **2914.46** | 565.73 | 0.3480 |
| **Min-Min** | 1000 | **1253.30** | 7455.47 | **263.41** | **0.7979** |
| CostGreedy | 1000 | 2865.71 | **5705.26** | 1186.07 | 0.3490 |

Output lengkap disimpan otomatis di `hasil_batch.csv` dan `hasil_vm_placement.txt`.
