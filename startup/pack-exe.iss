; NOT Toolbox Installer Script
; Inno Setup 6.7.0
;
; 默认构建 x64 安装包。ARM64 安装包使用：
;   ISCC.exe /DTargetArch=arm64 pack-exe.iss
;
; 注意：ARM64 安装包仍应先在 ARM64 Windows 上用 ARM64 Python 运行
; win-launcher-pyi.bat，确保 pyi\NTB\NTB.exe 是 ARM64 版本。

#ifndef TargetArch
  #define TargetArch "x64"
#endif

#if TargetArch == "arm64"
  #define TargetArchId "arm64"
  #define TargetSetupSuffix "win_arm64"
#else
  #define TargetArchId "x64"
  #define TargetSetupSuffix "win_x86-64"
#endif

[Setup]
AppId={{3189BC08-A081-436D-B344-A535CD722811}}
AppName=NOT Toolbox
AppVersion=1.0.7-RC1
AppPublisher=HOE Team
AppPublisherURL=https://hoe-team.github.io
AppSupportURL=https://hoe-team.xyz/contact-us.html
AppUpdatesURL=https://github.com/HOE-Team/not-toolbox/releases
DefaultDirName={autopf}\NOT Toolbox
DefaultGroupName=NOT Toolbox
AllowNoIcons=yes
LicenseFile=
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
OutputDir=.\installer
OutputBaseFilename=not_toolbox_setup_{#TargetSetupSuffix}
ArchitecturesAllowed={#TargetArchId}
ArchitecturesInstallIn64BitMode={#TargetArchId}
SetupIconFile=.\res-py\logo-install.ico
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
DisableProgramGroupPage=no
UninstallDisplayIcon={app}\logo-uninstall.ico
AppCopyright=Copyright (C) 2026 HOE Team. 保留部分权利。
VersionInfoVersion=1.0.6.5

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Files]
Source: ".\pyi\NTB\NTB.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: ".\pyi\NTB\_internal\*"; DestDir: "{app}\_internal"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "..\build\libs\NTB-shrunk.jar"; DestDir: "{app}\binary"; Flags: ignoreversion
Source: "binary_sha256.sha256"; DestDir: "{app}"; Flags: ignoreversion
Source: "binary_sha256.sha256.asc"; DestDir: "{app}"; Flags: ignoreversion skipifsourcedoesntexist
Source: ".\res-py\GoldenHoe_pub.asc"; DestDir: "{app}\res"; Flags: ignoreversion
Source: ".\res-py\FINGERPRINT.txt"; DestDir: "{app}\res"; Flags: ignoreversion
Source: ".\res-py\logo-uninstall.ico"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\NOT Toolbox"; Filename: "{app}\NTB.exe"; WorkingDir: "{app}"; IconFilename: "{app}\NTB.exe"
Name: "{userdesktop}\NOT Toolbox"; Filename: "{app}\NTB.exe"; WorkingDir: "{app}"; IconFilename: "{app}\NTB.exe"
Name: "{group}\Uninstall NOT Toolbox"; Filename: "{uninstallexe}"; IconFilename: "{app}\logo-uninstall.ico"

[Registry]
Root: "HKA"; Subkey: "Software\NOT Toolbox"; Flags: uninsdeletekeyifempty
Root: "HKA"; Subkey: "Software\NOT Toolbox"; ValueType: string; ValueName: "InstallPath"; ValueData: "{app}"; Flags: uninsdeletevalue

[Run]
Filename: "{app}\NTB.exe"; Description: "{cm:LaunchProgram,NOT Toolbox}"; Flags: nowait postinstall skipifsilent

; 卸载时删除的额外文件
[UninstallDelete]
Type: filesandordirs; Name: "{app}\config"

[Code]
procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssInstall then
  begin
    // 安装开始前的操作
    Log('Starting installation of NOT Toolbox...');
  end
  else if CurStep = ssPostInstall then
  begin
    // 安装完成后的操作
    Log('NOT Toolbox installation completed successfully!');
  end;
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usPostUninstall then
  begin
    // 卸载完成后的清理操作
    Log('NOT Toolbox has been uninstalled.');
  end;
end;