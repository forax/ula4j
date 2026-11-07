import pathlib, subprocess, sys

basics_dir = pathlib.Path(sys.argv[1])
python_exe = sys.executable  # the downloaded Python

for py_file in basics_dir.glob("*.py"):
    golden_file = py_file.with_suffix(".golden")
    result = subprocess.run(
        [python_exe, str(py_file)],
        capture_output=True,
        text=True,
        timeout=30
    )
    golden_file.write_text(result.stdout)
    print(f"Generated {golden_file}")
