; NOT Toolbox Installer Script
; Inno Setup 6.7.0

[Setup]
AppId={{3189BC08-A081-436D-B344-A535CD722811}}
AppName=NOT Toolbox
AppVersion=1.0.0
AppPublisher=HOE Team
AppPublisherURL=https://hoe-team.github.io
AppSupportURL=https://hoe-team.github.io/contact-us.html
AppUpdatesURL=https://github.com/HOE-Team/not-toolbox/releases
DefaultDirName={autopf}\NOT Toolbox
DefaultGroupName=NOT Toolbox
AllowNoIcons=yes
LicenseFile=
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
OutputDir=.\installer
OutputBaseFilename=NOT_Toolbox_Setup
SetupIconFile=.\res-py\logo.ico
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
DisableProgramGroupPage=no
UninstallDisplayIcon={app}\NTB.exe

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Files]
Source: ".\pyi\NTB\NTB.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: ".\pyi\NTB\_internal\*"; DestDir: "{app}\_internal"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "..\build\libs\NTB-all.jar"; DestDir: "{app}\binary"; Flags: ignoreversion

[Icons]
Name: "{group}\NOT Toolbox"; Filename: "{app}\NTB.exe"; WorkingDir: "{app}"; IconFilename: "{app}\NTB.exe"
Name: "{userdesktop}\NOT Toolbox"; Filename: "{app}\NTB.exe"; WorkingDir: "{app}"; IconFilename: "{app}\NTB.exe"
Name: "{group}\Uninstall NOT Toolbox"; Filename: "{uninstallexe}"

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