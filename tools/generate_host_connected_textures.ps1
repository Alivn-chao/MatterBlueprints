param(
    [string]$TextureDirectory = (Join-Path $PSScriptRoot '..\src\main\resources\assets\matterblueprints\textures\blocks'),
    [switch]$PanelsOnly
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
        [ValidateSet('Panel', 'Extend')]
        [string]$JoinMode,
        [int]$DetailInsetX = 0,
        [int]$DetailInsetY = 0,
        [System.Drawing.Color]$PanelColor
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

                        if ($JoinMode -eq 'Panel' -and $mask -eq 0) {
                            $color = $source.GetPixel($x, $y)
                        } elseif ($openCount -ge 2) {
                            # A true outside corner keeps both exposed frame edges.
                            $color = $source.GetPixel($x, $y)
                        } elseif ($openTop -or $openBottom) {
                            # Continue a horizontal outside frame through an internal tile seam.
                            $color = $source.GetPixel(8, $y)
                        } elseif ($openLeft -or $openRight) {
                            # Continue a vertical outside frame through an internal tile seam.
                            $color = $source.GetPixel($x, $frameOffset + 8)
                        } elseif ($JoinMode -eq 'Panel' -and (
                            $x -lt $DetailInsetX -or $x -ge 16 - $DetailInsetX -or
                            $frameY -lt $DetailInsetY -or $frameY -ge 16 - $DetailInsetY)) {
                            # The recessed detail sits inside one shared panel, with no frame on internal edges.
                            $color = $PanelColor
                        } elseif ($JoinMode -eq 'Extend' -and ($onTop -or $onRight -or $onBottom -or $onLeft)) {
                            $sourceX = [Math]::Min(15 - $BorderWidth, [Math]::Max($BorderWidth, $x))
                            $sourceFrameY = [Math]::Min(15 - $BorderWidth, [Math]::Max($BorderWidth, $frameY))
                            $color = $source.GetPixel($sourceX, $frameOffset + $sourceFrameY)
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

Write-ConnectedVariants 'host_casing.png' 'host_casing_ctm' 2 'Panel' 4 5 ([System.Drawing.Color]::FromArgb(32, 38, 46))
Write-ConnectedVariants 'host_cooling_fan.png' 'host_cooling_fan_ctm' 2 'Panel' 3 3 ([System.Drawing.Color]::FromArgb(23, 30, 40))
Write-ConnectedVariants 'host_casing_emissive.png' 'host_casing_ctm_emissive' 2 'Panel' 4 5 ([System.Drawing.Color]::FromArgb(0, 0, 0, 0))
Write-ConnectedVariants 'host_cooling_fan_emissive.png' 'host_cooling_fan_ctm_emissive' 2 'Panel' 3 3 ([System.Drawing.Color]::FromArgb(0, 0, 0, 0))

for ($mask = 0; $mask -lt 16; $mask++) {
    Move-Item -LiteralPath (Join-Path $TextureDirectory "host_casing_ctm_emissive_${mask}.png") `
        -Destination (Join-Path $TextureDirectory "host_casing_ctm_${mask}_emissive.png") -Force
    Move-Item -LiteralPath (Join-Path $TextureDirectory "host_cooling_fan_ctm_emissive_${mask}.png") `
        -Destination (Join-Path $TextureDirectory "host_cooling_fan_ctm_${mask}_emissive.png") -Force
}

if ($PanelsOnly) { return }

Write-ConnectedVariants 'host_casing_light.png' 'host_casing_light_ctm' 3 'Extend'
Write-ConnectedVariants 'host_casing_light_emissive.png' 'host_casing_light_ctm_emissive' 3 'Extend'
Write-ConnectedVariants 'host_casing_light_vertical.png' 'host_casing_light_vertical_ctm' 3 'Extend'
Write-ConnectedVariants 'host_casing_light_vertical_emissive.png' 'host_casing_light_vertical_ctm_emissive' 3 'Extend'

for ($mask = 0; $mask -lt 16; $mask++) {
    Move-Item -LiteralPath (Join-Path $TextureDirectory "host_casing_light_ctm_emissive_${mask}.png") `
        -Destination (Join-Path $TextureDirectory "host_casing_light_ctm_${mask}_emissive.png") -Force
    Move-Item -LiteralPath (Join-Path $TextureDirectory "host_casing_light_vertical_ctm_emissive_${mask}.png") `
        -Destination (Join-Path $TextureDirectory "host_casing_light_vertical_ctm_${mask}_emissive.png") -Force
    Copy-Item -LiteralPath (Join-Path $TextureDirectory 'host_casing_light.png.mcmeta') `
        -Destination (Join-Path $TextureDirectory "host_casing_light_ctm_${mask}.png.mcmeta") -Force
    Copy-Item -LiteralPath (Join-Path $TextureDirectory 'host_casing_light_emissive.png.mcmeta') `
        -Destination (Join-Path $TextureDirectory "host_casing_light_ctm_${mask}_emissive.png.mcmeta") -Force
    Copy-Item -LiteralPath (Join-Path $TextureDirectory 'host_casing_light_vertical.png.mcmeta') `
        -Destination (Join-Path $TextureDirectory "host_casing_light_vertical_ctm_${mask}.png.mcmeta") -Force
    Copy-Item -LiteralPath (Join-Path $TextureDirectory 'host_casing_light_vertical_emissive.png.mcmeta') `
        -Destination (Join-Path $TextureDirectory "host_casing_light_vertical_ctm_${mask}_emissive.png.mcmeta") -Force
}
