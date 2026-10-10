# V7 — Root-cause fix: low-end Host bandwidth

## Root cause found from the V6 output
The last VM to fail was VM 29. In V6 the VM creation order is:
- 5 Large
- 10 Medium
- 15 Small

The host CPU/RAM/storage capacities are enough. The blocking resource is Bandwidth.

Original low-end Host BW:
- 3 low hosts × 1000 Mbps
- each Small VM requests 500 Mbps
- a low host can therefore host at most 2 Small VMs by bandwidth

After the large and medium VMs are placed, the remaining PE/RAM pattern can leave 14 Small VMs placeable, while the 15th Small VM has no suitable host.

## V7 fix
Low-end Host BW is changed from 1000 → 2000 Mbps.

The architecture remains heterogeneous:
- High: 10000 Mbps
- Mid: 5000 Mbps
- Low: 2000 Mbps

This allows a low-end host to hold up to 3 Small VMs by bandwidth (3 × 500 = 1500 Mbps), while still remaining below the medium and high host classes.

No change to:
- task dataset
- task size range
- VM counts
- VM CPU/RAM/storage
- Min-Min concept
- CostGreedy comparison
- CloudletSchedulerSpaceShared
- no CPU overcommit (host PE total 57 > VM PE demand 55)

## Important
The V5/V6 CSVs must not be used as final results.
First verify V7 creates all 30 VMs and completes all cloudlets.
