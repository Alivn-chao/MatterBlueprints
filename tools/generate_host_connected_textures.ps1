param(
    [string]$TextureDirectory = (Join-Path $PSScriptRoot '..\src\main\resources\assets\matterblueprints\textures\blocks')
)

Add-Type -AssemblyName System.Drawing

$edgeTop = 1
$edgeRight = 2
$edgeBottom = 4
$edgeLeft = 8
$borderWidth = 2

function Write-ConnectedVariants {
    param(
        [string]$InputName,
        [string]$OutputSuffix,
        [bool]$UseTransparentJoin
    )

    $inputPath = Join-Path $TextureDirectory $InputName
    $source = [System.Drawing.Bitmap]::FromFile((Resolve-Path $inputPath))
    try {
        if ($source.Width -ne 16 -or $source.Height -ne 16) {
            throw "$InputName must be a 16x16 texture"
        }

        $joinColor = if ($UseTransparentJoin) {
            [System.Drawing.Color]::FromArgb(0, 0, 0, 0)
        } else {
            $source.GetPixel(4, 4)
        }

        for ($mask = 0; $mask -lt 16; $mask++) {
            $target = New-Object System.Drawing.Bitmap 16, 16, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
            try {
                for ($y = 0; $y -lt 16; $y++) {
                    for ($x = 0; $x -lt 16; $x++) {
                        $onTop = $y -lt $borderWidth
                        $onRight = $x -ge (16 - $borderWidth)
                        $onBottom = $y -ge (16 - $borderWidth)
                        $onLeft = $x -lt $borderWidth

                        $joinsConnectedEdge =
                            (($mask -band $edgeTop) -ne 0 -and $onTop) -or
                            (($mask -band $edgeRight) -ne 0 -and $onRight) -or
                            (($mask -band $edgeBottom) -ne 0 -and $onBottom) -or
                            (($mask -band $edgeLeft) -ne 0 -and $onLeft)
                        $touchesOpenBoundary =
                            (($mask -band $edgeTop) -eq 0 -and $onTop) -or
                            (($mask -band $edgeRight) -eq 0 -and $onRight) -or
                            (($mask -band $edgeBottom) -eq 0 -and $onBottom) -or
                            (($mask -band $edgeLeft) -eq 0 -and $onLeft)

                        $color = if ($joinsConnectedEdge -and -not $touchesOpenBoundary) {
                            $joinColor
                        } else {
                            $source.GetPixel($x, $y)
                        }
                        $target.SetPixel($x, $y, $color)
                    }
                }

                $outputName = "host_casing_ctm_${mask}${OutputSuffix}.png"
                $outputPath = Join-Path $TextureDirectory $outputName
                $target.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
            } finally {
                $target.Dispose()
            }
        }
    } finally {
        $source.Dispose()
    }
}

Write-ConnectedVariants -InputName 'host_casing.png' -OutputSuffix '' -UseTransparentJoin $false
Write-ConnectedVariants -InputName 'host_casing_emissive.png' -OutputSuffix '_emissive' -UseTransparentJoin $true
