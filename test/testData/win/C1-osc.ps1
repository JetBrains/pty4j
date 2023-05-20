[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::Write("Begin")
[Console]::Write("$([char]0x9D)1341;$("A" * [int]$env:A_CNT)$([char]0x9C)")
[Console]::Write("End")
