package sim;

import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicyFirstFit;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerSpaceShared;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Simulasi Cloud Task Scheduling: Min-Min vs Cost-Based Greedy (CloudSim Plus 7.3.0).
 * Arsitektur mengikuti dokumen "Desain Awal Project Kelompok 1".
 *
 * Jalankan:
 *   mvn -q compile exec:java                       -> workload sintetis, batch
 *   mvn -q compile exec:java -Dexec.args="poisson" -> workload sintetis, Poisson arrival
 *   mvn -q compile exec:java -Dexec.args="batch data.csv" -> workload dari CSV
 *       (kolom: length,file_size,output_size,pes,arrival ; ada header)
 */
public class TaskSchedulingSim {

    // ---------- Host: High-end, Mid-end, Low-end ----------
    static final String[] HOST_CLASS = {"High", "Mid", "Low"};
    static final int[]  HOST_COUNT = {3, 4, 3};
    static final int[]  HOST_CORES = {8, 6, 3};
    static final long[] HOST_MIPS  = {3000, 2000, 1000};
    static final long[] HOST_RAM   = {32000, 16000, 8000};       // MB
    static final long[] HOST_STOR  = {1_000_000, 500_000, 250_000}; // MB (1000/500/250 GB)
    static final long[] HOST_BW    = {10000, 5000, 2000};        // Mbps

    // ---------- VM: Small, Medium, Large ----------
    static final String[] VM_NAME  = {"Small", "Medium", "Large"};
    static final int[]    VM_COUNT = {15, 10, 5};
    static final int[]    VM_PES   = {1, 2, 4};
    static final long[]   VM_MIPS  = {1000, 1500, 2500};
    static final long[]   VM_RAM   = {2048, 4096, 8192};
    static final long[]   VM_STOR  = {20_000, 40_000, 80_000};   // MB
    static final long[]   VM_BW    = {500, 1000, 2000};

    // ---------- Simulated cost assumptions (not provider pricing) ----------
    static final double[] COST_PER_MI = {0.00010, 0.00013, 0.00020}; // biaya compute per MI
    static final double[] COST_PER_KB = {0.00005, 0.00004, 0.00003}; // biaya transfer per KB

    static final int[] SCENARIOS = {100, 500, 1000};
    static final long SEED = 42;

    // ================= Model kecil untuk hasil =================
    static class Plan {
        int[] vmOf;                       // index VM untuk tiap task
        List<Integer> order = new ArrayList<>(); // urutan submit (urutan penjadwalan)
    }

    static class Result {
        String algo; int nTasks;
        double makespan, cost, avgWait, avgUtil, di, throughput;
    }

    static void validateArchitecture() {
        int hostPes = 0;
        for (int i = 0; i < HOST_COUNT.length; i++) {
            hostPes += HOST_COUNT[i] * HOST_CORES[i];
        }

        int vmPes = 0;
        for (int i = 0; i < VM_COUNT.length; i++) {
            vmPes += VM_COUNT[i] * VM_PES[i];
        }

        if (vmPes > hostPes) {
            throw new IllegalStateException(
                "VM PE demand (" + vmPes + ") exceeds host PE capacity (" + hostPes + ")."
            );
        }

        System.out.println("=== ARCHITECTURE CHECK ===");
        System.out.println("Host PEs : " + hostPes);
        System.out.println("VM PEs   : " + vmPes);
        System.out.println("Capacity : OK");
        System.out.println("Host BW  : High=10000, Mid=5000, Low=2000 Mbps");
        System.out.println("VM BW    : Small=500, Medium=1000, Large=2000 Mbps");
    }

    static List<Host> createHosts() {
        List<Host> hosts = new ArrayList<>();
        for (int c = 0; c < 3; c++) {
            for (int h = 0; h < HOST_COUNT[c]; h++) {
                List<Pe> pes = new ArrayList<>();
                for (int p = 0; p < HOST_CORES[c]; p++) pes.add(new PeSimple(HOST_MIPS[c]));
                Host host = new HostSimple(HOST_RAM[c], HOST_BW[c], HOST_STOR[c], pes);
                // space-shared: each host PE is allocated exclusively to one VM; total host PEs = 57 and VM demand = 55
                host.setVmScheduler(new VmSchedulerSpaceShared());
                hosts.add(host);
            }
        }
        return hosts;
    }

    static List<Vm> createVms(List<Integer> vmType) {
        // 30 VM heterogen; VM creation order remains Large -> Medium -> Small
        // Placement order is Large -> Medium -> Small so the high-capacity VMs
        // are allocated before smaller VMs consume fragmented PE capacity.
        // The VM types and counts are unchanged: 15 Small, 10 Medium, 5 Large.
        List<Vm> vms = new ArrayList<>();
        int[] creationOrder = {2, 1, 0}; // Large, Medium, Small
        for (int t : creationOrder) {
            for (int k = 0; k < VM_COUNT[t]; k++) {
                Vm vm = new VmSimple(VM_MIPS[t], VM_PES[t]);
                vm.setRam(VM_RAM[t]).setBw(VM_BW[t]).setSize(VM_STOR[t]);
                vm.setDescription(VM_NAME[t]);
                vm.setCloudletScheduler(new CloudletSchedulerSpaceShared()); // non-preemptive
                vms.add(vm);
                if (vmType != null) vmType.add(t);
            }
        }
        return vms;
    }

    static void reportVmPlacement() {
        CloudSim sim = new CloudSim();
        List<Host> hosts = createHosts();
        Datacenter dc = new DatacenterSimple(sim, hosts, new VmAllocationPolicyFirstFit());
        dc.getCharacteristics().setArchitecture("x86").setOs("Linux").setVmm("Xen");
        dc.setTimeZone(7);

        DatacenterBroker broker = new DatacenterBrokerSimple(sim);
        List<Vm> vms = createVms(null);

        Map<Host, List<Vm>> hostVmMap = new LinkedHashMap<>();
        for (Host h : hosts) {
            hostVmMap.put(h, new ArrayList<>());
        }
        for (Vm vm : vms) {
            vm.addOnHostAllocationListener(evt -> {
                hostVmMap.computeIfAbsent(evt.getHost(), k -> new ArrayList<>()).add(evt.getVm());
            });
        }

        broker.submitVmList(vms);
        sim.start();

        if (broker.getVmCreatedList().size() != vms.size()) {
            throw new IllegalStateException(
                "VM Placement failed: only " + broker.getVmCreatedList().size() +
                " of " + vms.size() + " VMs were placed. Check host capacity / policy."
            );
        }

        System.out.println("\n=== VM PLACEMENT REPORT (First-Fit) ===");
        String header = String.format("%-8s %-6s %12s %14s %13s  %-30s",
                "Host", "Kelas", "Kapasitas PE", "VM Terpasang", "PE Terpakai", "Rincian VM");
        System.out.println(header);
        System.out.println("-".repeat(header.length()));

        int hostIdx = 0;
        int totalPesCap = 0;
        int totalPesUsed = 0;
        int totalVms = 0;

        for (int c = 0; c < 3; c++) {
            for (int h = 0; h < HOST_COUNT[c]; h++) {
                Host host = hosts.get(hostIdx);
                List<Vm> assigned = hostVmMap.get(host);
                if (assigned == null || assigned.isEmpty()) {
                    assigned = host.getVmCreatedList();
                }

                int peCap = HOST_CORES[c];
                int vmCount = assigned != null ? assigned.size() : 0;
                int peUsed = 0;
                int countSmall = 0, countMed = 0, countLarge = 0;

                if (assigned != null) {
                    for (Vm v : assigned) {
                        peUsed += v.getNumberOfPes();
                        String desc = v.getDescription();
                        if ("Large".equalsIgnoreCase(desc)) countLarge++;
                        else if ("Medium".equalsIgnoreCase(desc)) countMed++;
                        else if ("Small".equalsIgnoreCase(desc)) countSmall++;
                    }
                }

                List<String> details = new ArrayList<>();
                if (countLarge > 0) details.add(countLarge + "x Large");
                if (countMed > 0) details.add(countMed + "x Medium");
                if (countSmall > 0) details.add(countSmall + "x Small");
                String detailStr = details.isEmpty() ? "-" : String.join(", ", details);

                System.out.println(String.format("%-8s %-6s %10d PE %11d VM %10d PE  %-30s",
                        "Host " + hostIdx, HOST_CLASS[c], peCap, vmCount, peUsed, detailStr));

                totalPesCap += peCap;
                totalPesUsed += peUsed;
                totalVms += vmCount;
                hostIdx++;
            }
        }
        System.out.println("-".repeat(header.length()));
        System.out.println(String.format("TOTAL   : %d Host, %d PE Kapasitas | %d VM Terpasang, %d PE Terpakai (Sisa %d PE)",
                hosts.size(), totalPesCap, totalVms, totalPesUsed, (totalPesCap - totalPesUsed)));
        System.out.println("STATUS  : Semua " + vms.size() + " VM berhasil ditempatkan ke Host secara statis (First-Fit OK).\n");

        // Simpan juga ke file text hasil_vm_placement.txt
        try (PrintWriter pw = new PrintWriter("hasil_vm_placement.txt")) {
            pw.println("=== VM PLACEMENT REPORT (First-Fit Allocation) ===");
            pw.println(header);
            pw.println("-".repeat(header.length()));
            hostIdx = 0;
            for (int c = 0; c < 3; c++) {
                for (int h = 0; h < HOST_COUNT[c]; h++) {
                    Host host = hosts.get(hostIdx);
                    List<Vm> assigned = hostVmMap.get(host);
                    if (assigned == null || assigned.isEmpty()) assigned = host.getVmCreatedList();
                    int peCap = HOST_CORES[c];
                    int vmCount = assigned != null ? assigned.size() : 0;
                    int peUsed = 0;
                    int countSmall = 0, countMed = 0, countLarge = 0;
                    if (assigned != null) {
                        for (Vm v : assigned) {
                            peUsed += v.getNumberOfPes();
                            String desc = v.getDescription();
                            if ("Large".equalsIgnoreCase(desc)) countLarge++;
                            else if ("Medium".equalsIgnoreCase(desc)) countMed++;
                            else if ("Small".equalsIgnoreCase(desc)) countSmall++;
                        }
                    }
                    List<String> details = new ArrayList<>();
                    if (countLarge > 0) details.add(countLarge + "x Large");
                    if (countMed > 0) details.add(countMed + "x Medium");
                    if (countSmall > 0) details.add(countSmall + "x Small");
                    String detailStr = details.isEmpty() ? "-" : String.join(", ", details);
                    pw.println(String.format("%-8s %-6s %10d PE %11d VM %10d PE  %-30s",
                            "Host " + hostIdx, HOST_CLASS[c], peCap, vmCount, peUsed, detailStr));
                    hostIdx++;
                }
            }
            pw.println("-".repeat(header.length()));
            pw.println(String.format("TOTAL   : %d Host, %d PE Kapasitas | %d VM Terpasang, %d PE Terpakai (Sisa %d PE)",
                    hosts.size(), totalPesCap, totalVms, totalPesUsed, (totalPesCap - totalPesUsed)));
            pw.println("STATUS  : Semua " + vms.size() + " VM berhasil ditempatkan ke Host secara statis (First-Fit OK).");
        } catch (Exception ignored) {
        }
    }

    public static void main(String[] args) throws Exception {
        validateArchitecture();
        reportVmPlacement();
        boolean poisson = args.length > 0 && args[0].equalsIgnoreCase("poisson");
        String csv = null;
        if (args.length > 1) {
            csv = args[1];
        } else if (args.length == 1 && !poisson && args[0].toLowerCase().endsWith(".csv")) {
            csv = args[0];
        }

        List<Result> all = new ArrayList<>();
        for (int n : SCENARIOS) {
            double[][] wl = (csv != null) ? loadCsv(csv, n) : generateWorkload(n, poisson);
            all.add(run("Min-Min", n, wl));
            all.add(run("CostGreedy", n, wl));
        }
        printAndSave(all, poisson ? "poisson" : "batch");
    }

    // ================= Workload =================
    /** Kolom: [0]=length(MI) [1]=file KB [2]=output KB [3]=PE [4]=arrival(s) */
    static double[][] generateWorkload(int n, boolean poisson) {
        Random rnd = new Random(SEED);
        double[][] w = new double[n][5];
        double t = 0, lambda = 2.0; // rata-rata 2 task/detik untuk Poisson
        for (int i = 0; i < n; i++) {
            w[i][0] = 1000 + rnd.nextInt(99001);       // 1.000 - 100.000 MI
            w[i][1] = 300 + rnd.nextInt(4701);         // 300 - 5.000 KB
            w[i][2] = 300 + rnd.nextInt(4701);
            w[i][3] = rnd.nextDouble() < 0.8 ? 1 : 2;  // 1-2 PE
            if (poisson) { t += -Math.log(1 - rnd.nextDouble()) / lambda; }
            w[i][4] = poisson ? t : 0;
        }
        return w;
    }

    static double[][] loadCsv(String path, int n) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get(path));
        List<double[]> rows = new ArrayList<>();
        for (int i = 1; i < lines.size() && rows.size() < n; i++) {
            String[] p = lines.get(i).trim().split(",");
            if (p.length < 4) continue;
            rows.add(new double[]{Double.parseDouble(p[0]), Double.parseDouble(p[1]),
                    Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                    p.length > 4 ? Double.parseDouble(p[4]) : 0});
        }
        return rows.toArray(new double[0][]);
    }

    // ================= Satu kali simulasi =================
    static Result run(String algo, int n, double[][] wl) {
        CloudSim sim = new CloudSim();

        // Datacenter: 10 host heterogen
        List<Host> hosts = createHosts();
        Datacenter dc = new DatacenterSimple(sim, hosts, new VmAllocationPolicyFirstFit());
        dc.getCharacteristics().setArchitecture("x86").setOs("Linux").setVmm("Xen");
        dc.setTimeZone(7);

        DatacenterBroker broker = new DatacenterBrokerSimple(sim);

        List<Integer> vmType = new ArrayList<>();
        List<Vm> vms = createVms(vmType);

        // Task (cloudlet)
        List<Cloudlet> tasks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Cloudlet c = new CloudletSimple((long) wl[i][0], (int) wl[i][3]);
            c.setFileSize((long) wl[i][1]).setOutputSize((long) wl[i][2]);
            c.setSubmissionDelay(wl[i][4]);
            tasks.add(c);
        }

        // Jadwalkan
        Plan plan = algo.equals("Min-Min")
                ? minMin(tasks, vms, vmType, wl)
                : costGreedy(tasks, vms, vmType, wl);

        List<Cloudlet> ordered = new ArrayList<>();
        for (int idx : plan.order) {
            Cloudlet c = tasks.get(idx);
            c.setVm(vms.get(plan.vmOf[idx]));
            ordered.add(c);
        }
        broker.submitVmList(vms);
        broker.submitCloudletList(ordered);
        sim.start();

        if (broker.getVmCreatedList().size() != vms.size()) {
            throw new IllegalStateException(
                algo + ": only " + broker.getVmCreatedList().size() +
                " of " + vms.size() + " VMs were created. Results are invalid."
            );
        }

        return computeMetrics(algo, n, tasks, vms, vmType, plan);
    }

    // ================= Estimasi =================
    /**
     * Estimasi waktu eksekusi satu Cloudlet pada satu VM.
     * CloudSim Plus mendefinisikan Cloudlet length sebagai MI yang dieksekusi
     * pada setiap PE yang diminta, sehingga waktu dasar per PE adalah length / MIPS.
     */
    static double execTime(Cloudlet c, Vm vm) {
        return (double) c.getLength() / vm.getMips();
    }

    /** Cost = compute (length x tarif/MI) + transfer ((file + output) x tarif/KB). */
    static double taskCost(Cloudlet c, int type) {
        return c.getLength() * COST_PER_MI[type]
                + (c.getFileSize() + c.getOutputSize()) * COST_PER_KB[type];
    }

    /** VM layak jika jumlah vCPU >= PE yang dibutuhkan task (resource capacity constraint). */
    static boolean feasible(Cloudlet c, Vm vm) {
        return vm.getNumberOfPes() >= c.getNumberOfPes();
    }

    // ================= Algoritma 1: Min-Min =================
    static Plan minMin(List<Cloudlet> tasks, List<Vm> vms, List<Integer> vmType, double[][] wl) {
        int n = tasks.size(), m = vms.size();
        Plan plan = new Plan();
        plan.vmOf = new int[n];
        double[] ready = new double[m];
        boolean[] done = new boolean[n];

        for (int step = 0; step < n; step++) {
            int bestTask = -1, bestVm = -1;
            double bestCt = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                if (done[i]) continue;
                // VM dengan completion time minimum untuk task i
                int minVm = -1; double minCt = Double.MAX_VALUE;
                for (int j = 0; j < m; j++) {
                    if (!feasible(tasks.get(i), vms.get(j))) continue;
                    double ct = Math.max(ready[j], wl[i][4]) + execTime(tasks.get(i), vms.get(j));
                    if (ct < minCt) { minCt = ct; minVm = j; }
                }
                // pilih task dengan minimum-of-minimum
                if (minCt < bestCt) { bestCt = minCt; bestTask = i; bestVm = minVm; }
            }
            done[bestTask] = true;
            plan.vmOf[bestTask] = bestVm;
            plan.order.add(bestTask);
            ready[bestVm] = bestCt;
        }
        return plan;
    }

    // ================= Algoritma 2: Cost Based Greedy =================
    static Plan costGreedy(List<Cloudlet> tasks, List<Vm> vms, List<Integer> vmType, double[][] wl) {
        int n = tasks.size(), m = vms.size();
        Plan plan = new Plan();
        plan.vmOf = new int[n];
        double[] ready = new double[m];

        for (int i = 0; i < n; i++) {
            int bestVm = -1; double bestCost = Double.MAX_VALUE;
            for (int j = 0; j < m; j++) {
                if (!feasible(tasks.get(i), vms.get(j))) continue;
                double cost = taskCost(tasks.get(i), vmType.get(j));
                // tie-break: VM dengan ready time paling kecil (agar tidak selalu VM #0).
                // Hapus kondisi kedua kalau mau versi "murni" yang menumpuk di 1 VM.
                boolean better = cost < bestCost - 1e-12
                        || (Math.abs(cost - bestCost) <= 1e-12 && ready[j] < ready[bestVm]);
                if (better) { bestCost = cost; bestVm = j; }
            }
            plan.vmOf[i] = bestVm;
            plan.order.add(i);
            ready[bestVm] = Math.max(ready[bestVm], wl[i][4]) + execTime(tasks.get(i), vms.get(bestVm));
        }
        return plan;
    }

    // ================= Metrik =================
    static Result computeMetrics(String algo, int n, List<Cloudlet> tasks, List<Vm> vms,
                                 List<Integer> vmType, Plan plan) {
        Result r = new Result();
        r.algo = algo; r.nTasks = n;

        int m = vms.size();
        double[] vmFinish = new double[m];
        double[] vmBusy = new double[m];
        double waitSum = 0, cost = 0, makespan = 0;

        int finishedCount = 0;
        for (int i = 0; i < n; i++) {
            Cloudlet c = tasks.get(i);
            if (!c.isFinished()) {
                throw new IllegalStateException(
                    algo + ": cloudlet " + i + " did not finish. " +
                    "Simulation results are invalid; check VM placement/scheduling."
                );
            }

            finishedCount++;
            int j = plan.vmOf[i];
            // In CloudSim Plus 7.3.0, CloudletSchedulerSpaceShared can report an inflated finishTime
            // (Long.MAX_VALUE / MIPS) when a VM has idle PEs at simulation shutdown.
            // For non-preemptive execution, the true completion time is execStartTime + actualCpuTime.
            double actualFinish = c.getExecStartTime() + c.getActualCpuTime();
            makespan = Math.max(makespan, actualFinish);
            vmFinish[j] = Math.max(vmFinish[j], actualFinish);
            vmBusy[j] += c.getActualCpuTime() * c.getNumberOfPes();
            double wait = Math.max(0.0, c.getExecStartTime() - c.getSubmissionDelay());
            waitSum += wait;
            cost += taskCost(c, vmType.get(j));
        }

        if (finishedCount != n || makespan <= 0) {
            throw new IllegalStateException(algo + ": invalid simulation result.");
        }

        /*
         * Conservative sanity bound for the current batch model.
         * Every 1-PE task can execute on a 1-PE, 1000-MIPS VM.
         * Every 2-PE task can execute on a 2-PE, 1500-MIPS VM.
         * Serial execution on those slowest eligible VM types is an upper bound
         * when all arrivals are zero and host PE allocation is space-shared.
         */
        if (tasks.stream().allMatch(c -> c.getSubmissionDelay() == 0)) {
            double serialUpperBound = 0;
            for (Cloudlet c : tasks) {
                serialUpperBound += c.getLength() /
                    (c.getNumberOfPes() == 1 ? 1000.0 : 1500.0);
            }
            if (makespan > serialUpperBound * 1.05) {
                throw new IllegalStateException(
                    algo + ": makespan " + makespan +
                    " s exceeds conservative sanity bound " + serialUpperBound +
                    " s. Do not use this result for evaluation."
                );
            }
        }

        double utilSum = 0, tMax = 0, tMin = Double.MAX_VALUE;
        int activeVms = 0;
        for (int j = 0; j < m; j++) {
            if (vmFinish[j] <= 0) continue;
            activeVms++;
            utilSum += vmBusy[j] / (makespan * vms.get(j).getNumberOfPes());
            tMax = Math.max(tMax, vmFinish[j]);
            tMin = Math.min(tMin, vmFinish[j]);
        }

        r.makespan = makespan;
        r.cost = cost;
        r.avgWait = waitSum / n;
        r.avgUtil = 100.0 * utilSum / m;

        // Normalized Degree of Imbalance in [0,1] over active VMs.
        r.di = activeVms <= 1 ? 0 : (tMax - tMin) / tMax;
        r.throughput = n / makespan;
        return r;
    }

    // ================= Output =================
    static void printAndSave(List<Result> all, String tag) throws Exception {
        String header = String.format("%-11s %6s %12s %12s %12s %9s %8s %12s",
                "Algoritma", "Task", "Makespan(s)", "Cost", "AvgWait(s)", "Util(%)", "DI", "Thr(task/s)");
        System.out.println("\n" + header);
        System.out.println("-".repeat(header.length()));
        try (PrintWriter pw = new PrintWriter("hasil_" + tag + ".csv")) {
            pw.println("algoritma,task,makespan,cost,avg_wait,util_pct,di,throughput");
            for (Result r : all) {
                System.out.println(String.format("%-11s %6d %12.2f %12.2f %12.2f %9.2f %8.3f %12.4f",
                        r.algo, r.nTasks, r.makespan, r.cost, r.avgWait, r.avgUtil, r.di, r.throughput));
                pw.println(String.format(java.util.Locale.US, "%s,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.6f",
                        r.algo, r.nTasks, r.makespan, r.cost, r.avgWait, r.avgUtil, r.di, r.throughput));
            }
        }
        System.out.println("\nHasil disimpan ke hasil_" + tag + ".csv");
        System.out.println("Catatan: Cost adalah simulated cost berdasarkan asumsi COST_PER_MI dan COST_PER_KB.");
    }
}
