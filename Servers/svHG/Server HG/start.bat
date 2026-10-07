@echo off
setlocal
cd /d "%~dp0"
title Server HG

REM ===== RAM DEL SERVIDOR =====
REM Cambia 8G por la RAM maxima deseada, por ejemplo 4G, 8G o 16G.
REM Usa al menos 1G. La memoria inicial es 1G y aumenta segun se necesita.
set "RAM=8G"

REM Nombre del archivo del servidor.
set "SERVER_JAR=paper-1.21.4-232.jar"

where java >nul 2>&1
if errorlevel 1 (
    echo No se encontro Java. Instala Java y agregalo al PATH.
    goto end
)

if not exist "%SERVER_JAR%" (
    echo No se encontro el archivo "%SERVER_JAR%" en esta carpeta.
    goto end
)

echo Iniciando Server HG con un maximo de %RAM% de RAM...
echo Para cerrar correctamente el servidor, escribe stop en la consola.
echo.
java -Xms1G -Xmx%RAM% -jar "%SERVER_JAR%" --nogui

:end
echo.
echo El servidor esta detenido.
pause
endlocal
