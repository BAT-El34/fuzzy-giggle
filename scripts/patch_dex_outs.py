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


def get_code_offsets(buf):
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

    return sorted(set(code_offsets))


# Instruction widths in 16-bit code units for every opcode emitted by the
# DraftWA custom DEX builder.
WIDTH = {
    0x01: 1, 0x07: 1, 0x0a: 1, 0x0b: 1, 0x0c: 1, 0x0e: 1,
    0x0f: 1, 0x11: 1, 0x12: 1, 0x21: 1, 0x81: 1,
    0x13: 2, 0x16: 2, 0x1a: 2, 0x1f: 2, 0x22: 2, 0x29: 2,
    0x32: 2, 0x33: 2, 0x34: 2, 0x35: 2, 0x36: 2, 0x37: 2,
    0x38: 2, 0x39: 2, 0x46: 2, 0x52: 2, 0x54: 2, 0x59: 2,
    0x5b: 2, 0x90: 2, 0x91: 2, 0x92: 2, 0x9b: 2, 0xd8: 2,
    0x14: 3, 0x6e: 3, 0x6f: 3, 0x70: 3, 0x71: 3, 0x72: 3,
}


def patch_dex(data):
    buf = bytearray(data)
    if buf[:4] != b'dex\n':
        raise SystemExit('classes2.dex is not a DEX file')

    invoke_swaps = 0
    outs_changes = 0

    for code_off in get_code_offsets(buf):
        registers_size = struct.unpack_from('<H', buf, code_off)[0]
        old_outs = struct.unpack_from('<H', buf, code_off + 4)[0]
        insns_size = struct.unpack_from('<I', buf, code_off + 12)[0]
        insns_base = code_off + 16

        pos = 0
        max_invoke_args = 0
        while pos < insns_size:
            word = struct.unpack_from('<H', buf, insns_base + pos * 2)[0]
            opcode = word & 0xff
            if opcode not in WIDTH:
                raise SystemExit(
                    f'Unknown opcode 0x{opcode:02x} at code_off=0x{code_off:x}, unit={pos}'
                )

            if 0x6e <= opcode <= 0x72:
                # Bug in the old builder: format 35c was encoded as
                # opcode | argument_count<<8 | G<<12. Dalvik specifies
                # opcode | G<<8 | argument_count<<12.
                argument_count = (word >> 8) & 0xf
                g_register = (word >> 12) & 0xf
                max_invoke_args = max(max_invoke_args, argument_count)
                corrected = opcode | (g_register << 8) | (argument_count << 12)
                if corrected != word:
                    struct.pack_into('<H', buf, insns_base + pos * 2, corrected)
                    invoke_swaps += 1

            pos += WIDTH[opcode]

        if pos != insns_size:
            raise SystemExit(f'Instruction stream overrun at code_off=0x{code_off:x}')
        if max_invoke_args > registers_size:
            raise SystemExit(
                f'invoke args {max_invoke_args} > registers {registers_size} at code_off=0x{code_off:x}'
            )

        # outs_size is the maximum number of argument words used by any
        # outgoing invoke in this method, not a constant value.
        if old_outs != max_invoke_args:
            struct.pack_into('<H', buf, code_off + 4, max_invoke_args)
            outs_changes += 1

    # DEX signature and checksum cover the mutated bytes.
    buf[12:32] = hashlib.sha1(buf[32:]).digest()
    struct.pack_into('<I', buf, 8, zlib.adler32(buf[12:]) & 0xffffffff)

    return bytes(buf), invoke_swaps, outs_changes


def patch_apk(source, destination):
    with zipfile.ZipFile(source, 'r') as zin:
        patched_dex, invoke_swaps, outs_changes = patch_dex(zin.read('classes2.dex'))

        with zipfile.ZipFile(destination, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zout:
            for info in zin.infolist():
                name = info.filename
                if name.upper().startswith('META-INF/'):
                    continue
                payload = patched_dex if name == 'classes2.dex' else zin.read(name)
                zout.writestr(name, payload)

    print(f'invoke_35c_swaps={invoke_swaps} outs_size_changes={outs_changes}')
    print('classes2_sha256=' + hashlib.sha256(patched_dex).hexdigest())


if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit('usage: patch_dex_outs.py INPUT.apk OUTPUT.apk')
    patch_apk(sys.argv[1], sys.argv[2])
