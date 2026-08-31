param(
    [string]$TextureDirectory = (Join-Path $PSScriptRoot '..\src\main\resources\assets\matterblueprints\textures\blocks')
)

Add-Type -AssemblyName System.Drawing

$edgeTop = 1
$edgeRight = 2
$edgeBottom = 4
$edgeLeft = 8

function Write-ConnectedVariants {
    param(
        [string]$InputName,
        [string]$OutputPrefix,
        [int]$BorderWidth,
        [ValidateSet('Solid', 'Extend')]
        [string]$JoinMode,
        [bool]$UseTransparentJoin
    )

    $inputPath = Join-Path $TextureDirectory $InputName
    $source = [System.Drawing.Bitmap]::FromFile((Resolve-Path $inputPath))
    try {
        if ($source.Width -ne 16 -or $source.Height % 16 -ne 0) {
            throw "$InputName must be a 16-pixel-wide texture with 16x16 animation frames"
        }

        for ($mask = 0; $mask -lt 16; $mask++) {
            $target = New-Object System.Drawing.Bitmap 16, $source.Height, (
                [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
            try {
                for ($y = 0; $y -lt $source.Height; $y++) {
                    $frameY = $y % 16
                    $frameOffset = $y - $frameY
                    for ($x = 0; $x -lt 16; $x++) {
                        $onTop = $frameY -lt $BorderWidth
                        $onRight = $x -ge (16 - $BorderWidth)
                        $onBottom = $frameY -ge (16 - $BorderWidth)
                        $onLeft = $x -lt $BorderWidth
                        $openTop = $onTop -and ($mask -band $edgeTop) -eq 0
                        $openRight = $onRight -and ($mask -band $edgeRight) -eq 0
                        $openBottom = $onBottom -and ($mask -band $edgeBottom) -eq 0
                        $openLeft = $onLeft -and ($mask -band $edgeLeft) -eq 0
                        $openCount = @($openTop, $openRight, $openBottom, $openLeft).Where({ $_ }).Count

                        if ($openCount -ge 2) {
                            # A true outside corner keeps both exposed frame edges.
                            $color = $source.GetPixel($x, $y)
                        } elseif ($openTop -or $openBottom) {
                            # Continue a horizontal outside frame through an internal tile seam.
                            $color = $source.GetPixel(8, $y)
                        } elseif ($openLeft -or $openRight) {
                            # Continue a vertical outside frame through an internal tile seam.
                            $color = $source.GetPixel($x, $frameOffset + 8)
                        } elseif ($onTop -or $onRight -or $onBottom -or $onLeft) {
                            if ($JoinMode -eq 'Extend') {
                                $sourceX = [Math]::Min(15 - $BorderWidth, [Math]::Max($BorderWidth, $x))
                                $sourceFrameY = [Math]::Min(
                                    15 - $BorderWidth,
                                    [Math]::Max($BorderWidth, $frameY))
                                $color = $source.GetPixel($sourceX, $frameOffset + $sourceFrameY)
                            } elseif ($UseTransparentJoin) {
                                $color = [System.Drawing.Color]::FromArgb(0, 0, 0, 0)
                            } else {
                                $color = $source.GetPixel(4, $frameOffset + 4)
                            }
                        } else {
                            $color = $source.GetPixel($x, $y)
                        }
                        $target.SetPixel($x, $y, $color)
                    }
                }

                $outputPath = Join-Path $TextureDirectory "${OutputPrefix}_${mask}.png"
                $target.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
            } finally {
                $target.Dispose()
            }
        }
    } finally {
        $source.Dispose()
    }
}

Write-ConnectedVariants 'host_casing.png' 'host_casing_ctm' 2 'Solid' $false
Write-ConnectedVariants 'host_casing_emissive.png' 'host_casing_ctm_emissive' 2 'Solid' $true
Write-ConnectedVariants 'host_casing_light.png' 'host_casing_light_ctm' 3 'Extend' $false
Write-ConnectedVariants 'host_casing_light_emissive.png' 'host_casing_light_ctm_emissive' 3 'Extend' $false

for ($mask = 0; $mask -lt 16; $mask++) {
    Move-Item -LiteralPath (Join-Path $TextureDirectory "host_casing_ctm_emissive_${mask}.png") `
        -Destination (Join-Path $TextureDirectory "host_casing_ctm_${mask}_emissive.png") -Force
    Move-Item -LiteralPath (Join-Path $TextureDirectory "host_casing_light_ctm_emissive_${mask}.png") `
        -Destination (Join-Path $TextureDirectory "host_casing_light_ctm_${mask}_emissive.png") -Force
    Copy-Item -LiteralPath (Join-Path $TextureDirectory 'host_casing_light.png.mcmeta') `
        -Destination (Join-Path $TextureDirectory "host_casing_light_ctm_${mask}.png.mcmeta") -Force
    Copy-Item -LiteralPath (Join-Path $TextureDirectory 'host_casing_light_emissive.png.mcmeta') `
        -Destination (Join-Path $TextureDirectory "host_casing_light_ctm_${mask}_emissive.png.mcmeta") -Force
}
