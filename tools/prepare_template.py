#!/usr/bin/env python3
"""Prepare the official DD Form 2642 for on-device filling.

The form published by DoD is a hybrid XFA/AcroForm PDF with owner-password
restrictions. Viewers that honour XFA ignore AcroForm values, so this script
writes a copy with the XFA packet removed and no encryption. Field names,
widgets and page content are left untouched.

Usage: python3 tools/prepare_template.py <official.pdf> app/src/main/assets/forms/dd2642_template.pdf
Requires: pip install pypdf cryptography
"""
import sys
from pypdf import PdfReader, PdfWriter
from pypdf.generic import NameObject, BooleanObject


def main(src: str, dst: str) -> None:
    reader = PdfReader(src)
    if reader.is_encrypted:
        reader.decrypt("")
    writer = PdfWriter(clone_from=reader)
    form = writer._root_object["/AcroForm"]
    if "/XFA" in form:
        del form["/XFA"]
    form[NameObject("/NeedAppearances")] = BooleanObject(False)
    for key in ("/Perms",):
        if key in writer._root_object:
            del writer._root_object[key]
    with open(dst, "wb") as fh:
        writer.write(fh)
    print(f"wrote {dst}: {len(writer.pages)} pages")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
