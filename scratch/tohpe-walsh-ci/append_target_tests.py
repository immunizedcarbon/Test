from pathlib import Path

path = Path("src/t_opt.rs")
source = path.read_text()

source += r'''

#[cfg(test)]
mod tohpe_walsh_tests {
    use super::*;
    use std::time::Instant;

    struct Rng(u64);

    impl Rng {
        fn next(&mut self) -> u64 {
            self.0 ^= self.0 << 13;
            self.0 ^= self.0 >> 7;
            self.0 ^= self.0 << 17;
            self.0
        }
    }

    fn bit_vector(value: usize, nb_qubits: usize) -> BitVector {
        let mut result = BitVector::new(nb_qubits);
        for bit in 0..nb_qubits {
            if value & (1 << bit) != 0 {
                result.xor_bit(bit);
            }
        }
        result
    }

    fn y_vector(len: usize) -> BitVector {
        let mut y = BitVector::new(len);
        for i in (0..len).step_by(2) {
            y.xor_bit(i);
        }
        y
    }

    fn pair_key(table: &Vec<BitVector>, y: &BitVector) -> BitVector {
        let mut map = HashMap::new();
        let parity = y.popcount() & 1 == 1;
        for i in 0..table.len() {
            if parity && !y.get(i) {
                map.insert(table[i].get_integer_vec(), 1);
            }
            else if !parity && y.get(i) {
                map.insert(table[i].get_integer_vec(), 1);
            }
        }
        for i in 0..table.len() {
            if !y.get(i) { continue; }
            for j in 0..table.len() {
                if y.get(j) { continue; }
                let mut z = table[i].clone();
                z.xor(&table[j]);
                let score = map.entry(z.get_integer_vec()).or_insert(0);
                *score += 2;
            }
        }
        let mut max_cost = 0;
        let mut max_key: Option<&Vec<i128>> = None;
        for (key, val) in map.iter() {
            if *val > max_cost || (*val == max_cost && max_key.is_some() && key < max_key.unwrap()) {
                max_cost = *val;
                max_key = Some(key);
            }
        }
        BitVector::from_integer_vec(max_key.unwrap().to_vec())
    }

    fn unique_table(nb_qubits: usize, len: usize, seed: u64) -> Vec<BitVector> {
        let size = 1usize << nb_qubits;
        let mut rng = Rng(seed);
        let mut used = vec![false; size];
        let mut table = Vec::with_capacity(len);
        while table.len() < len {
            let value = (rng.next() as usize % (size - 1)) + 1;
            if !used[value] {
                used[value] = true;
                table.push(bit_vector(value, nb_qubits));
            }
        }
        table
    }

    #[test]
    fn tohpe_walsh_matches_bitvector_pair_scoring() {
        for nb_qubits in 4..=12 {
            let size = 1usize << nb_qubits;
            for seed in 0..100u64 {
                let len = size - 1;
                let table = unique_table(nb_qubits, len, seed + nb_qubits as u64 * 1000 + 1);
                let y = y_vector(len);
                let expected = pair_key(&table, &y).get_integer_vec();
                let actual = best_walsh_key(&table, &y, nb_qubits).unwrap().get_integer_vec();
                assert_eq!(expected, actual, "nb_qubits={nb_qubits} seed={seed}");
            }
        }
    }

    #[test]
    fn tohpe_walsh_matches_duplicate_columns() {
        for nb_qubits in 2..=8 {
            let size = 1usize << nb_qubits;
            for seed in 0..100u64 {
                let mut rng = Rng(0x517cc1b727220a95 ^ seed ^ ((nb_qubits as u64) << 32));
                let len = size * 4;
                let table = (0..len)
                    .map(|_| bit_vector(rng.next() as usize % size, nb_qubits))
                    .collect::<Vec<_>>();
                let y = y_vector(len);
                let expected = pair_key(&table, &y).get_integer_vec();
                let actual = best_walsh_key(&table, &y, nb_qubits).unwrap().get_integer_vec();
                assert_eq!(expected, actual, "nb_qubits={nb_qubits} seed={seed}");
            }
        }
    }

    #[test]
    fn tohpe_walsh_bitvector_benchmark() {
        for &(nb_qubits, len) in &[(12, 3517), (14, 4096)] {
            let table = unique_table(nb_qubits, len, 0x9e3779b97f4a7c15 ^ len as u64);
            let y = y_vector(len);

            let start = Instant::now();
            let expected = pair_key(&table, &y).get_integer_vec();
            let pair_time = start.elapsed();
            let start = Instant::now();
            let actual = best_walsh_key(&table, &y, nb_qubits).unwrap().get_integer_vec();
            let walsh_time = start.elapsed();
            assert_eq!(expected, actual);
            println!(
                "target-types n={nb_qubits} m={len} pair={:.6}s walsh={:.6}s speedup={:.1}x",
                pair_time.as_secs_f64(),
                walsh_time.as_secs_f64(),
                pair_time.as_secs_f64() / walsh_time.as_secs_f64()
            );
        }
    }
}
'''

path.write_text(source)
