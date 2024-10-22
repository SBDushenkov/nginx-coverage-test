#!/usr/bin/bash

### ПРИ ЛЮБОЙ ОШИБКЕ ПРЕРЫВАЕМ ВЫПОЛНЕНИЕ
set -e

tmp_dir=$(pwd)

### ПОДГОТОВКА ПУТЕЙ
### ЗДЕСЬ ЛЕЖАТ НАШИ СОБСТВЕННЫЕ ТЕСТЫ НА JAVA. ПО УМОЛЧАНИЮ СКРИПТ ЗАПУСКАЕТСЯ УЖЕ ИЗ ДИРЕКТОРИИ С ТЕСТАМИ
if [ "$JAVA_TEST_DIR" = '' ]; then
    echo "JAVA_TEST_DIR not set"
    JAVA_TEST_DIR=${tmp_dir}
fi
echo "JAVA_TEST_DIR=${JAVA_TEST_DIR}"

### ПРОВЕРКА, ЧТО НАШИ СОБСТВЕННЫЕ ТЕСТЫ, ДЕЙСТВИТЕЛЬНО ЛЕЖАТ ПО УКАЗАННОМУ АДРЕСУ
if [ ! -f "$JAVA_TEST_DIR"/pom.xml ]; then
    echo "pom.xml not found ${JAVA_TEST_DIR} not found. exiting"  1>&2
    exit 1
fi

### ПО УМОЛЧАНИЮ КОД NGINX ЛЕЖИТ В ДИРЕКТОРИИ source ОТНОСИТЕЛЬНО ТЕКУЩЕЙ
if [ "$SOURCE_DIR" = '' ]; then
    echo "SOURCE_DIR not set"
    SOURCE_DIR=${tmp_dir}/source
fi
echo "SOURCE_DIR=${SOURCE_DIR}"

### ЗДЕСЬ БУДУТ РЕЗУЛЬТАТЫ ВЫПОЛНЕНИЯ СКРИПТА. ПО УМОЛЧАНИЮ ДИРЕКТОРИЯ nginx ОТНОСИТЕЛЬНО ТЕКУЩЕЙ
if [ "$WORK_DIR" = '' ]; then
    echo "WORK_DIR not set"
    WORK_DIR=${tmp_dir}/nginx
fi
echo "WORK_DIR=${WORK_DIR}"

### ЕСЛИ ДИРЕКТОРИИ С РЕЗУЛЬТАТАМИ ВЫПОЛНЕНИЯ СКРИПТА НЕ СУЩЕСТВУЕТ, СОЗДАЕМ ЕЁ
if [ ! -d "$WORK_DIR" ]; then
    mkdir "${WORK_DIR}"
fi

### ДИРЕКТОРИЯ ДЛЯ ТЕСТОВОЙ КОМПИЛЯЦИИ NGINX
coverage_dir="${WORK_DIR:?}"/coverage
### ДИРЕКТОРИЯ C ОТЧЕТАМИ О ПОКРЫТИИ КОДА
report_dir="${WORK_DIR:?}"/report
### ДИРЕКТОРИЯ C ВЫВОДОМ САНИТАЙЗЕРА
sanitizer_log="${WORK_DIR:?}"/sanitizer-log
### ДИРЕКТОРИЯ C ОТЧЕТОМ О ВЫПОЛНЕНИИ ТЕСТОВ
test_result_dir="${WORK_DIR:?}"/test-result

### ОЧИСТКА ДИРЕКТОРИИ С РЕЗУЛЬТАТАМИ ВЫПОЛНЕНИЯ СКРИПТА. НА СЛУЧАЙ, ЕСЛИ ДИРЕКТОРИЯ БЫЛА НЕ ПУСТА
### ПОДГОТОВКА ДИРЕКТОРИИ ДЛЯ ВЫВОДА РЕЗУЛЬТАТОВ
rm -rf "${coverage_dir:?}"
rm -rf "${report_dir:?}"
rm -rf "${test_result_dir:?}"
rm -rf "${sanitizer_log:?}"

mkdir "${report_dir:?}"
mkdir "${test_result_dir:?}"
mkdir "${sanitizer_log:?}"

### ПОРТ ПО УМОЛЧАНИЮ. DEFAULT НЕ ПОДХОДЯТ, Т.К. ТРЕБУЮТ root ПРАВА
if [ "$DEFAULT_HTTP_PORT" = '' ]; then
  echo "DEFAULT_HTTP_PORT not set"
  DEFAULT_HTTP_PORT=13081
fi
echo "DEFAULT_HTTP_PORT=${DEFAULT_HTTP_PORT}"

### ЕСЛИ КОД NGINX ОТСУТСТВУЕТ, СКАЧИВАНИЕ ИЗ GIT
if [ ! -d "$SOURCE_DIR" ]; then
    echo "source not found. downloading"
    mkdir "$SOURCE_DIR"
    cd "$SOURCE_DIR" || exit 
    git clone --depth=1 --branch=master https://github.com/nginx/nginx.git .
    rm -rf .git
    echo "source created"
fi

rsync -av --progress "$SOURCE_DIR/" "$coverage_dir" --exclude '.git'
 
cd "$coverage_dir" || exit

prefix="$coverage_dir"/nginx-install/

echo "configuring dbg with prefix=${prefix}"

#--builddir=./target/ должно быть относительным и внутри. нужно разбираться

#sudo apt-get install zlib1g-dev libpcre3 libpcre3-dev libbz2-dev libssl-dev build-essential
#sudo apt install libssl-dev

### КОНФИГУРИРОВАНИЕ Makefile ДЛЯ СБОРКИ NGINX
### ОТКЛЮЧЕНА ОПТИМИЗАЦИЯ (ДЛЯ АНАЛИЗА ЛОГОВ)
### ВКЛЮЧАЕМ ОПЦИИ КОМПИЛЯЦИИ (ПОКРЫТИЕ, САНИТАЙЗЕР)
### ВКЛЮЧАЕМ НЕОБХОДИМЫЕ ВСТРОЕННЫЕ МОДУЛИ, ИСКЛЮЧАЕМ НЕИСПОЛЬЗУЕМЫЕ*
### * НУЖНЫЕ МОДУЛИ ВКЛЮЧЕНЫ ПО УМОЛЧАНИЮ

./auto/configure \
--with-cc-opt='-O0 --coverage -fsanitize=address -static-libasan -g ' \
--prefix="${prefix}" \
--build=nginx-dbg \
--builddir=./target/ \
--with-debug \
--with-http_v2_module \
--without-http_charset_module \
--without-http_gzip_module \
--without-http_ssi_module \
--without-http_userid_module \
--without-http_access_module \
--without-http_auth_basic_module \
--without-http_mirror_module \
--without-http_autoindex_module \
--without-http_geo_module \
--without-http_map_module \
--without-http_split_clients_module \
--without-http_referer_module \
--without-http_proxy_module \
--without-http_fastcgi_module \
--without-http_uwsgi_module \
--without-http_scgi_module \
--without-http_grpc_module \
--without-http_memcached_module \
--without-http_limit_conn_module \
--without-http_limit_req_module \
--without-http_empty_gif_module \
--without-http_browser_module \
--without-http_upstream_hash_module \
--without-http_upstream_ip_hash_module \
--without-http_upstream_least_conn_module \
--without-http_upstream_random_module \
--without-http_upstream_keepalive_module \
--without-http_upstream_zone_module \
\
--without-http-cache \
\
--without-mail_pop3_module \
--without-mail_imap_module \
--without-mail_smtp_module \
 \
--without-stream_geo_module \
--without-stream_map_module \
--without-stream_split_clients_module \
--without-stream_return_module \
--without-stream_set_module \
--without-stream_upstream_hash_module \
--without-stream_upstream_least_conn_module \
--without-stream_upstream_random_module \
--without-stream_upstream_zone_module

### ДОБАВЛЯЕМ ИНСТРУКЦИИ ЛИНКОВЩИКУ ДЛЯ РАБОТЫ САНИТАЙЗЕРА И ПОКРЫТИЯ КОДА
### ВЫПОЛНЯЕМ КОМПИЛЯЦИЮ И ИНСТАЛЛИРУЕМ NGINX

echo "configuration dbg done"

echo "patching makefile"
sed -i 's/\t-Wl,-E/\t-Wl,-E --coverage -fsanitize=address -static-libasan -g/' ./target/Makefile

echo "installing"
make -f ./Makefile install

### ГОТОВИМСЯ К ПЕРВОМУ ЗАПУСКУ NGINX. ПРАВИМ ПОРТ В КОНФИГУРАЦИОННОМ ФАЙЛЕ ПО УМОЛЧАНИЮ.
### ЭТОТ ФАЙЛ НУЖЕН ТОЛЬКО ДЛЯ ЗАПУСАК, В ТЕСТАХ ОН БУДЕТ ДИНАМИЧЕСКИ ЗАМЕНЯТЬСЯ
echo "patching conf for initial start"
port_mod='s/        listen       80;/        listen       '${DEFAULT_HTTP_PORT}';/'
sed -i \
  "${port_mod}" \
  ./nginx-install/conf/nginx.conf

echo "starting nginx"

pgrep -u "${USER}" nginx | while read -r pid ; do
  kill -9 "${pid}"
done

### ЗАПУСК NGINX
# 1. Чтобы отлаживались внешние библиотеки linux нужен su. Нам анализ printf не нужен
# 2. https://forum.nginx.org/read.php?21,291313,291315#msg-291315
# Практика есть, но конкретно leak sanitizer бесполезен примерно
  #полностью: реальных утечек в nginx'е он не ловит, так как
  #используются pool allocator'ы, но при этом ругается на любыые
  #аллокации, не освобождённые явно перед выходом. Что делать всегда
  #не обязательно (при выходе процесса вся выделенная память
  #освобождается автоматически), а в некоторых случаях и вообще
  #невозможно (скажем, память, выделенную под какой-нибудь environ,
  #освобождать нельзя, она используется при выходе). Хорошее решение -
  #выключить leak sanitizer и забыть.
# 3. Дополнительной проверки на вывод sanitizer не требуется, т.к. если встретится ошибка,
# то при вызове nginx будет возвращен код ошибки - команда не будет выполнена успешно
export ASAN_OPTIONS="detect_leaks=0:log_path=./target/nginx:verbosity=1"

./target/nginx -t || {
    echo "failed to start nginx. conf file" 1>&2
    exit 1
}

./target/nginx || {
    echo "failed to start nginx. no result generated" 1>&2
    exit 1
}

### ЗАПУСК ТЕСТОВ
cd "$JAVA_TEST_DIR"
mvn clean || result=$(($?))

if [ "$result" = "" ]; then
    mvn surefire-report:report || result=$(($?))
    cp -r ./target/reports/* "${test_result_dir}"

    if grep -r -n -i --include='*.txt' 'FAILURE!' ./target/surefire-reports/
    then
        failures="There is test failures"
    fi
fi

cd "$coverage_dir"
echo "stopping nginx"
./target/nginx -s quit
mv ./target/log* "${sanitizer_log}"

### ЕСЛИ БЫЛИ ОШИБКИ ВЫПОЛНЕНИЯ ТЕСТОВ, СООБЩАЕМ
echo "processing results"

if [ "$failures" != "" ]; then
    echo "${failures}" 1>&2
    exit 3
fi

### ФОРМИРОВАНИЕ ОТЧЕТА О ПОКРЫТИИ КОДА
lcov -t "report" -o report.info -c -d .
genhtml report.info -o "${report_dir}"

#mkdir "${report_dir}"/gcovr-html
#gcovr  --root ../ --txt -s --output "${report_dir}"/gcpvr-report.txt | tee "${report_dir}"/summary.txt
#
#gcovr  \
#--root ../ \
#--html-details --html-medium-threshold 30 \
#--html --html-high-threshold 60 \
#--output "${report_dir}"/gcovr-html/report.html

echo "SUCCESS"