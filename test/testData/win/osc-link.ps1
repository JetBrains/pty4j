[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::Write("Begin")
[Console]::Write("$([char]0x1b)]8;;http://$("A" * 200).com$([char]0x07)This is a link$([char]0x1b)]8;;$([char]0x07)")
[Console]::Write("End")
