# RUNBOOK DEMO 1 — SOKA
## Cloud Task Scheduling — Min-Min

### A. STATUS IMPLEMENTASI
Versi final demo ini:
- Java 17 target
- CloudSim Plus 7.3.0
- 1 Datacenter
- 10 Host heterogen
- 30 VM heterogen
- 100 / 500 / 1000 task
- Algoritma utama: Min-Min
- Pembanding: Cost-Based Greedy
- CloudletSchedulerSpaceShared
- VmSchedulerSpaceShared
- Synthetic dataset, seed 42
- Output: Makespan, Cost, Avg Waiting Time, Utilization, DI, Throughput

### B. KENAPA ADA PERUBAHAN HOST?
Draft awal memakai 46 host PEs, sedangkan VM membutuhkan 55 vCPU.
Karena scheduler host SpaceShared tidak boleh mengalokasikan lebih banyak PE daripada kapasitas host,
demo final memakai:
- High-end: 3 host × 8 PE
- Mid-end: 4 host × 6 PE
- Low-end: 3 host × 3 PE
Total = 57 PE.
Ini harus disamakan juga pada slide/proposal sebelum demo.

### C. BUKA PROJECT DI VS CODE
1. File → Open Folder.
2. Pilih folder project `cloudsim-scheduling`.
3. Pastikan:
   `pom.xml`
   `dataset_synthetic_1000.csv`
   `src/main/java/sim/TaskSchedulingSim.java`

### D. CEK JAVA DAN MAVEN
Buka Terminal VS Code:

```bash
java -version
mvn -version
```

Target Java: 17.
Maven harus terdeteksi.

### E. COMPILE
```bash
mvn -q clean compile
```

Kalau sukses, tidak ada error compilation.

### F. RUN DEMO
```bash
mvn -q exec:java -Dexec.args="batch dataset_synthetic_1000.csv"
```

Program menjalankan tiga skenario:
- 100 task
- 500 task
- 1000 task

Untuk setiap skenario:
- Min-Min
- Cost-Based Greedy

Hasil disimpan ke:
`hasil_batch.csv`

### G. YANG DIREKAM SEBAGAI VIDEO BACKUP
Mulai screen recording sebelum command run.
Rekam:
1. terminal
2. `mvn -q clean compile`
3. command run
4. seluruh hasil
5. file `hasil_batch.csv`

Video ini adalah backup untuk nomor 2 apabila live simulation bermasalah.

### H. SAAT ZOOM DEMO
1. Mulai Zoom meeting.
2. Pastikan recording sudah ON sebelum presentasi.
3. Presentasi algoritma.
4. Tampilkan source code.
5. Run simulation secara live.
6. Jika live gagal, play video backup.
7. Tampilkan hasil.
8. Tampilkan tabel proposal vs implementasi.
9. Tutup demo.
10. Stop recording setelah seluruh sesi selesai.

### I. YOUTUBE
Upload rekaman Zoom, bukan hanya video backup.
Visibility: Unlisted.
Copy URL ke Demo Sheet.

### J. JAWABAN JIKA DOSEN BERTANYA DATASET
“Untuk Demo 1 kami menggunakan synthetic workload yang reproducible dengan seed 42.
Google Cluster Trace kami posisikan sebagai pengembangan dataset real untuk tahap lanjutan,
sementara demo ini menggunakan workload terkontrol agar perilaku algoritma mudah direplikasi.”

### K. JAWABAN JIKA DITANYA COST
“Cost yang digunakan pada demo merupakan simulated cost berdasarkan parameter asumsi
COST_PER_MI dan COST_PER_KB, bukan tarif provider cloud aktual.”

### L. JAWABAN JIKA DITANYA KENAPA HOST/VM SPEK DIUBAH
“Pada draft awal terdapat 46 physical PE, sedangkan total kebutuhan VM adalah 55 vCPU.
Agar setup konsisten dengan constraint kapasitas resource, kami melakukan penyesuaian
jumlah PE host dan memperbarui slide setup menjadi total 57 PE.”
