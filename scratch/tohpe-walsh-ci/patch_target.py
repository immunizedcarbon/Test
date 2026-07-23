from pathlib import Path

path = Path("src/t_opt.rs")
source = path.read_text()

imports = """use crate::bit_vector::BitVector;\nuse hashbrown::HashMap;\n"""
helpers = r'''

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

fn best_walsh_key(table: &Vec<BitVector>, y: &BitVector, nb_qubits: usize) -> Option<BitVector> {
    if nb_qubits > MAX_WALSH_QUBITS {
        return None;
    }
    let ones = y.popcount() as usize;
    let zeros = table.len() - ones;
    let size = 1usize << nb_qubits;
    let pairs = ones.saturating_mul(zeros);
    if pairs < size || pairs > (i64::MAX as usize) / size {
        return None;
    }

    let parity = ones & 1 == 1;
    let mut one = vec![0i64; size];
    let mut zero = vec![0i64; size];
    let mut bonus = vec![false; size];
    for i in 0..table.len() {
        let key = table[i].get_integer_vec()[0] as usize;
        if y.get(i) {
            one[key] += 1;
            if !parity {
                bonus[key] = true;
            }
        }
        else {
            zero[key] += 1;
            if parity {
                bonus[key] = true;
            }
        }
    }

    walsh_hadamard_transform(&mut one);
    walsh_hadamard_transform(&mut zero);
    for i in 0..size {
        one[i] *= zero[i];
    }
    walsh_hadamard_transform(&mut one);

    let mut max_cost = 0;
    let mut max_key = 0;
    for key in 0..size {
        let cost = 2 * one[key] / size as i64 + bonus[key] as i64;
        if cost > max_cost {
            max_cost = cost;
            max_key = key;
        }
    }

    let mut z = BitVector::new_block_size(table[0].blocks.len());
    for bit in 0..nb_qubits {
        if max_key & (1 << bit) != 0 {
            z.xor_bit(bit);
        }
    }
    Some(z)
}
'''

if helpers.strip() not in source:
    if imports not in source:
        raise SystemExit("import anchor not found")
    source = source.replace(imports, imports + helpers, 1)

old = r'''            let mut map = HashMap::new();
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
            if max_cost <= 0 { break; }
            let z = BitVector::from_integer_vec(max_key.unwrap().to_vec());
'''

new = r'''            let z = if let Some(z) = best_walsh_key(&table, &y, nb_qubits) {
                z
            }
            else {
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
                if max_cost <= 0 { break; }
                BitVector::from_integer_vec(max_key.unwrap().to_vec())
            };
'''

if old not in source:
    raise SystemExit("TOHPE score block not found")
source = source.replace(old, new, 1)
path.write_text(source)
