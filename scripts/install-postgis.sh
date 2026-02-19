# THANK YOU: https://clews.id.au/posts/setting-up-postgresql-16-and-postgis-on-macos-with-homebrew/
# Fix issues with missing gettext headers
brew reinstall gettext
brew unlink gettext && brew link gettext --force

# Install PostGIS dependencies
brew install geos gdal libxml2 sfcgal protobuf-c

# Download and build PostGIS 3.5.2 from source
wget https://download.osgeo.org/postgis/source/postgis-3.5.2.tar.gz
tar -xvzf postgis-3.5.2.tar.gz
rm postgis-3.5.2.tar.gz
cd postgis-3.5.2

# Use the correct gettext version
GETTEXT_VERSION=$(brew info gettext | grep -Eo 'stable [0-9]+\.[0-9]+' | awk '{print $2}')

./configure \
  --with-projdir=/opt/homebrew/opt/proj \
  --with-pgconfig=/opt/homebrew/opt/postgresql@16/bin/pg_config \
  --with-jsondir=/opt/homebrew/opt/json-c \
  --with-sfcgal=/opt/homebrew/opt/sfcgal/bin/sfcgal-config \
  --with-pcredir=/opt/homebrew/opt/pcre \
  --without-protobuf \
  --without-topology \
  LDFLAGS="$LDFLAGS -L/opt/homebrew/Cellar/gettext/$GETTEXT_VERSION/lib" \
  CFLAGS="-I/opt/homebrew/Cellar/gettext/$GETTEXT_VERSION/include"
make
make install
