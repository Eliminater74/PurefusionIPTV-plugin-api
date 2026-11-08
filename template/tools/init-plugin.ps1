param(
    [Parameter(Mandatory=$true)][string]$PluginId,
    [Parameter(Mandatory=$true)][string]$PluginName,
    [Parameter(Mandatory=$true)][string]$Publisher,
    [Parameter(Mandatory=$true)][string]$DeveloperId,
    [Parameter(Mandatory=$true)][string]$Description
)
$ErrorActionPreference = 'Stop'
& node (Join-Path $PSScriptRoot 'marketplace.cjs') init $PluginId $PluginName $Publisher $DeveloperId $Description
exit $LASTEXITCODE
