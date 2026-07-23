use std::collections::HashMap;
use std::time::Instant;

const MAX_WALSH_QUBITS: usize = 20;

fn walsh_hadamard_transform(values: &mut [i64]) {
    let mut half = 1;
    while half < values.len() {
        let step = half * 2;
        for start in (0..values.len()).step_by(step) {
            for i in start..start + half {
                let a = values[i];
                let b = values[i + half];
                values[i] = a + b;
                values[i + half] = a - b;
            }
        }
        half = step;
    }
}

fn best_pair_score(table: &[usize], y: &[bool]) -> (i64, Option<usize>) {
    let parity = y.iter().filter(|&&v| v).count() & 1 == 1;
    let mut scores = HashMap::new();
    for (&z, &bit) in table.iter().zip(y) {
        if (parity && !bit) || (!parity && bit) {
            scores.insert(z, 1i64);
        }
    }
    for i in 0..table.len() {
        if !y[i] {
            continue;
        }
        for j in 0..table.len() {
            if y[j] {
                continue;
            }
            *scores.entry(table[i] ^ table[j]).or_insert(0) += 2;
        }
    }
    let mut best_score = 0;
    let mut best_z = None;
    for (&z, &score) in &scores {
        if score > best_score || (score == best_score && best_z.is_some_and(|old| z < old)) {
            best_score = score;
            best_z = Some(z);
        }
    }
    (best_score, best_z)
}

fn best_walsh_score(table: &[usize], y: &[bool], nb_qubits: usize) -> Option<(i64, usize)> {
    if nb_qubits > MAX_WALSH_QUBITS {
        return None;
    }
    let ones = y.iter().filter(|&&v| v).count();
    let zeros = table.len() - ones;
    let size = 1usize << nb_qubits;
    if ones.saturating_mul(zeros) < size {
        return None;
    }

    let parity = ones & 1 == 1;
    let mut one = vec![0i64; size];
    let mut zero = vec![0i64; size];
    let mut bonus = vec![false; size];
    for (&z, &bit) in table.iter().zip(y) {
        if bit {
            one[z] += 1;
            if !parity {
                bonus[z] = true;
            }
        } else {
            zero[z] += 1;
            if parity {
                bonus[z] = true;
            }
        }
    }

    walsh_hadamard_transform(&mut one);
    walsh_hadamard_transform(&mut zero);
    for i in 0..size {
        one[i] *= zero[i];
    }
    walsh_hadamard_transform(&mut one);

    let divisor = size as i64;
    let mut best_score = 0;
    let mut best_z = 0;
    for z in 0..size {
        let score = 2 * (one[z] / divisor) + i64::from(bonus[z]);
        if score > best_score {
            best_score = score;
            best_z = z;
        }
    }
    Some((best_score, best_z))
}

struct Rng(u64);

impl Rng {
    fn next(&mut self) -> u64 {
        self.0 ^= self.0 << 13;
        self.0 ^= self.0 >> 7;
        self.0 ^= self.0 << 17;
        self.0
    }
}

fn benchmark(n: usize, m: usize) {
    let size = 1usize << n;
    let mut rng = Rng(0x9e3779b97f4a7c15 ^ n as u64 ^ ((m as u64) << 32));
    let mut used = vec![false; size];
    let mut table = Vec::with_capacity(m);
    while table.len() < m {
        let z = (rng.next() as usize % (size - 1)) + 1;
        if !used[z] {
            used[z] = true;
            table.push(z);
        }
    }
    let y: Vec<bool> = (0..m).map(|_| rng.next() & 1 == 1).collect();

    let start = Instant::now();
    let pair = best_pair_score(&table, &y);
    let pair_time = start.elapsed();
    let start = Instant::now();
    let walsh = best_walsh_score(&table, &y, n).unwrap();
    let walsh_time = start.elapsed();
    assert_eq!(pair, (walsh.0, Some(walsh.1)));
    println!(
        "n={n:2} m={m:5} pair={:.6}s walsh={:.6}s speedup={:.1}x",
        pair_time.as_secs_f64(),
        walsh_time.as_secs_f64(),
        pair_time.as_secs_f64() / walsh_time.as_secs_f64()
    );
}

fn main() {
    for &(n, m) in &[
        (10, 512),
        (12, 2048),
        (12, 3517),
        (14, 2048),
        (16, 8192),
        (16, 15891),
        (18, 8192),
        (20, 8192),
    ] {
        benchmark(n, m);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn matches_pair_scoring_with_unique_columns() {
        for n in 2..=12 {
            let size = 1usize << n;
            for seed in 0..200u64 {
                let mut rng = Rng(seed + 1 + n as u64 * 1000);
                let m = (rng.next() as usize % (size - 1)) + 1;
                let mut used = vec![false; size];
                let mut table = Vec::with_capacity(m);
                while table.len() < m {
                    let z = (rng.next() as usize % (size - 1)) + 1;
                    if !used[z] {
                        used[z] = true;
                        table.push(z);
                    }
                }
                let y: Vec<bool> = (0..m).map(|_| rng.next() & 1 == 1).collect();
                let pair = best_pair_score(&table, &y);
                let walsh = best_walsh_score(&table, &y, n);
                if let Some((score, z)) = walsh {
                    assert_eq!(pair, (score, Some(z)), "n={n} seed={seed}");
                }
            }
        }
    }

    #[test]
    fn matches_pair_scoring_with_duplicate_columns() {
        for n in 2..=8 {
            let size = 1usize << n;
            for seed in 0..200u64 {
                let mut rng = Rng(0x517cc1b727220a95 ^ seed ^ ((n as u64) << 32));
                let m = size + (rng.next() as usize % (size * 3));
                let table: Vec<usize> = (0..m).map(|_| rng.next() as usize % size).collect();
                let y: Vec<bool> = (0..m).map(|i| i & 1 == 0).collect();
                let pair = best_pair_score(&table, &y);
                let walsh = best_walsh_score(&table, &y, n).unwrap();
                assert_eq!(pair, (walsh.0, Some(walsh.1)), "n={n} seed={seed}");
            }
        }
    }

    #[test]
    fn falls_back_outside_the_dense_small_dimension_regime() {
        assert_eq!(best_walsh_score(&[1, 2, 4], &[true, false, true], 3), None);
        assert_eq!(best_walsh_score(&[1 << 20, 1], &[true, false], 21), None);
    }
}
