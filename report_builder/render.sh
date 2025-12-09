#!/bin/bash

set -euo pipefail

script_dir="report_builder"

input_file="${script_dir}/report.md"
output_file="report.pdf"
csl_file="${script_dir}/gost-r-7-0-5-2008-numeric-iaa.csl"

if [[ ! -f "$input_file" ]]; then
  echo "Error: $input_file not found!"
  exit 1
fi

if [[ ! -f "$csl_file" ]]; then
  echo "Error: $csl_file not found!"
  exit 1
fi

pandoc "$input_file" \
  -o "$output_file" \
  --citeproc \
  --csl="$csl_file" \
  --pdf-engine=xelatex \
  -f markdown+raw_tex+tex_math_dollars

echo "Conversion successful: $input_file has been converted to $output_file."
