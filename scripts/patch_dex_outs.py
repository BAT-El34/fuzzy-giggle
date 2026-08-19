#!/usr/bin/env python3
import sys, struct, hashlib, zlib, zipfile


def uleb(buf, off):
    result = 0
    shift = 0
    while True:
        value = buf[off]
        off += 1
        result |= (value & 0x7f) << shift
        if value < 0x80:
            return result, off
        shift += 7


def patch_dex(data):
    buf = bytearray(data)
    if buf[:4] != b'dex\n':
        raise SystemExit('classes2.dex is not a DEX file')

    u4 = lambda off: struct.unpack_from('<I', buf, off)[0]
    class_defs_size = u4(96)
    class_defs_off = u4(100)
    code_offsets = []

    for i in range(class_defs_size):
        item = class_defs_off + i * 32
        class_data_off = u4(item + 24)
        if not class_data_off:
            continue

        pos = class_data_off
        static_fields, pos = uleb(buf, pos)
        instance_fields, pos = uleb(buf, pos)
        direct_methods, pos = uleb(buf, pos)
        virtual_methods, pos = uleb(buf, pos)

        for _ in range(static_fields + instance_fields):
            _, pos = uleb(buf, pos)
            _, pos = uleb(buf, pos)

        for count in (direct_methods, virtual_methods):
            method_idx = 0
            for _ in range(count):
                diff, pos = uleb(buf, pos)
                method_idx += diff
                _, pos = uleb(buf, pos)
                code_off, pos = uleb(buf, pos)
                if code_off:
                    code_offsets.append(code_off)

    changed = 0
    for code_off in sorted(set(code_offsets)):
        registers_size = struct.unpack_from('<H', buf, code_off)[0]
        outs_size = struct.unpack_from('<H', buf, code_off + 4)[0]
        corrected = min(outs_size, registers_size)
        if corrected != outs_size:
            struct.pack_into('<H', buf, code_off + 4, corrected)
            changed += 1

    # DEX signature and checksum cover the mutated bytes.
    buf[12:32] = hashlib.sha1(buf[32:]).digest()
    struct.pack_into('<I', buf, 8, zlib.adler32(buf[12:]) & 0xffffffff)

    invalid = []
    for code_off in sorted(set(code_offsets)):
        registers_size = struct.unpack_from('<H', buf, code_off)[0]
        outs_size = struct.unpack_from('<H', buf, code_off + 4)[0]
        if outs_size > registers_size:
            invalid.append((code_off, registers_size, outs_size))

    if invalid:
        raise SystemExit(f'Invalid code items remain: {invalid[:5]}')

    return bytes(buf), changed, len(set(code_offsets))


def patch_apk(source, destination):
    with zipfile.ZipFile(source, 'r') as zin:
        dex = zin.read('classes2.dex')
        patched_dex, changed, total = patch_dex(dex)

        with zipfile.ZipFile(destination, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zout:
            for info in zin.infolist():
                name = info.filename
                # Existing v1 signature becomes invalid after classes2.dex changes.
                if name.upper().startswith('META-INF/'):
                    continue
                payload = patched_dex if name == 'classes2.dex' else zin.read(name)
                zout.writestr(name, payload)

    print(f'patched_code_items={changed} total_code_items={total}')


if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit('usage: patch_dex_outs.py INPUT.apk OUTPUT.apk')
    patch_apk(sys.argv[1], sys.argv[2])
