from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile
from xml.sax.saxutils import escape


ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "samples"
OUT_FILE = OUT_DIR / "sample_questions.xlsx"

ROWS = [
    ["\u9898\u5e72", "\u7b54\u6848", "\u9009\u9879A", "\u9009\u9879B", "\u9009\u9879C", "\u9009\u9879D", "\u9009\u9879E", "\u9009\u9879F"],
    ["\u5b89\u5168\u5e3d\u7684\u4e3b\u8981\u4f5c\u7528\u662f\u4ec0\u4e48\uff1f", "A", "\u9632\u6b62\u5934\u90e8\u53d7\u4f24", "\u63d0\u9ad8\u5de5\u4f5c\u901f\u5ea6", "\u9632\u6b62\u8863\u670d\u5f04\u810f", "\u65b9\u4fbf\u8bc6\u522b\u5de5\u79cd", "", ""],
    ["\u53d1\u751f\u706b\u707e\u65f6\u5e94\u4f18\u5148\u62e8\u6253\u54ea\u4e2a\u7535\u8bdd\uff1f", "B", "110", "119", "120", "122", "", ""],
    ["\u8bbe\u5907\u8fd0\u884c\u4e2d\u53ef\u4ee5\u968f\u610f\u62c6\u9664\u9632\u62a4\u7f69\u3002", "\u9519\u8bef", "", "", "", "", "", ""],
    ["\u8fdb\u5165\u65bd\u5de5\u73b0\u573a\u5fc5\u987b\u6b63\u786e\u4f69\u6234\u5b89\u5168\u5e3d\u3002", "\u6b63\u786e", "", "", "", "", "", ""],
    ["\u6211\u56fd\u5b89\u5168\u751f\u4ea7\u65b9\u9488\u662f\u5b89\u5168\u7b2c\u4e00\u3001\u9884\u9632\u4e3a\u4e3b\u3001____\u3002", "\u7efc\u5408\u6cbb\u7406", "", "", "", "", "", ""],
    ["\u7535\u6c14\u8bbe\u5907\u7740\u706b\u65f6\uff0c\u5e94\u5148____\u518d\u706d\u706b\u3002", "\u5207\u65ad\u7535\u6e90", "", "", "", "", "", ""],
]


def cell_xml(row_index: int, col_index: int, value: str) -> str:
    col_name = chr(ord("A") + col_index)
    return f'<c r="{col_name}{row_index}" t="inlineStr"><is><t>{escape(value)}</t></is></c>'


def sheet_xml() -> str:
    rows = []
    for r, row in enumerate(ROWS, start=1):
        cells = "".join(cell_xml(r, c, value) for c, value in enumerate(row))
        rows.append(f'<row r="{r}">{cells}</row>')
    return (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
        '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">\n'
        "  <sheetData>\n"
        f"    {'\n    '.join(rows)}\n"
        "  </sheetData>\n"
        "</worksheet>\n"
    )


FILES = {
    "[Content_Types].xml": """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>
""",
    "_rels/.rels": """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>
""",
    "xl/workbook.xml": """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="\u9898\u5e93" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>
""",
    "xl/_rels/workbook.xml.rels": """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>
""",
    "xl/worksheets/sheet1.xml": sheet_xml(),
}


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    with ZipFile(OUT_FILE, "w", ZIP_DEFLATED) as archive:
        for path, content in FILES.items():
            archive.writestr(path, content.encode("utf-8"))
    print(f"Created {OUT_FILE}")


if __name__ == "__main__":
    main()
