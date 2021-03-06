#!/usr/bin/env bash
#---------------------------------------------------------------------------------------------------
# Fetches a database backup (default yesterday's) from S3 to the Docker container codekvast_database
#---------------------------------------------------------------------------------------------------

source $(dirname $0)/.check-requirements.sh

declare weekday=${1:-$(env LANG=en_US date -d "yesterday 13:00" --utc +%A | tr [A-Z] [a-z])}
declare tarball=mariadb-backup-${weekday}.tar.gz
declare mysql_datadir=~/.codekvast_database
declare s3_bucket="s3://io.codekvast.default.prod.backup"

declare tmp_dir=$(mktemp -d /tmp/fetch-database.XXXXXXX)
trap "rm -fr ${tmp_dir}" EXIT

s3cmd get ${s3_bucket}/${tarball} ${tmp_dir}

echo "docker stop codekvast_database"
docker stop codekvast_database

echo "Removing $mysql_datadir/*"
sudo rm -fr ${mysql_datadir}/*

echo "Unpacking ${tmp_dir}/${tarball} into ${mysql_datadir}/ ..."
sudo tar xf ${tmp_dir}/${tarball} -C ${mysql_datadir}

echo "Changing ownership of ${mysql_datadir}/ ..."
sudo chown -R ${USER}:"$(id -gn ${USER})" ${mysql_datadir}

echo "Starting a temporary MariaDB container without grant tables..."
declare container=$(docker run -d -v ${mysql_datadir}:/var/lib/mysql mariadb:10 --skip-grant-tables)

echo "Waiting for MariaDB to start..."
sleep 10

echo "Resetting passwords..."
docker exec ${container} mysql -e "
    use mysql;
    update user set password=PASSWORD('root') where User='root';
    update user set password=PASSWORD('codekvast') where User='codekvast';
    update user set plugin='mysql_native_password';"

echo "Stopping and removing temporary container..."
docker stop ${container}
docker rm -v ${container}

cd ..
./gradlew :product:login:startMariadb
