---
pdf-engine: xelatex
geometry: 
    - a4paper
    - bottom=1.5cm
    - includefoot
mainfont: "Times New Roman"
monofont: "DejaVu Sans Mono"
mathfont: "TeX Gyre Termes Math"
titlepage: true
titlepage-rule-height: 0
titlepage-color: "F4F460"  # Background color (Orange)
output: pdf_document
lang: "ru"
header-includes:
  - \usepackage{multirow}
  - \usepackage{placeins} 
  - \usepackage{array}  # Enables column width control
  - \renewcommand{\arraystretch}{1.5}  # Increases vertical cell spacing # This package helps keep tables in place
  - \usepackage{pdflscape}
  - \usepackage{longtable,booktabs}
  - \usepackage{tabularx}
---

\begin{titlepage}
\centering
{\Large \textbf{Федеральное государственное автономное образовательное учреждение высшего образования}}\\[0.5cm]
{\Large \textbf{Университет ИТМО}}\\[3cm]
{\Large Дисциплина:   Программирование встраиваемых систем}\\[0.5cm]
{\Huge Курсовая работа}\\

\vfill

\begin{flushright}
\textbf{Работу выполнили,\\
студенты группы P4219:}\\
Ануфриев Илья Владимирович,\\
Василев Васил Николаев,\\
Кадырин Вадим Юрьевич,\\
Суховей Ярослав Юрьевич\\[1cm]
\textbf{Преподаватель:}\\
к.т.н., доцент ПИКТ\\
Ключев Аркадий Олегович \\[3cm]
\end{flushright}

\vfill
2025 г.\\
Санкт-Петербург
\end{titlepage}

\newpage

\tableofcontents

\newpage

# Введение

Криптография — ключевой инструмент современной информационной безопасности, который обеспечивает конфиденциальность, целостность и подлинность данных в сетях и хранилищах. Актуальность темы определяется повсеместной цифровизацией и ростом угроз. Цель работы — дать краткое систематизированное представление о базовых понятиях и практических подходах криптографии, необходимое для корректного выбора алгоритмов и параметров. Объект исследования — криптографические методы защиты информации.

\newpage
