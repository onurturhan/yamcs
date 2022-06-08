# sudo apt -y install maven
# sudo apt-get install -y openjdk-11-jdk

export JAVA_HOME=/usr/lib/jvm/default-java

# mkdir config 
# cd config
# git clone https://github.com/yamcs/quickstart.git  t6a_smu
# cd t6a_smu && rm -rif .git && cd ../..

# git clone https://github.com/yamcs/yamcs-sle.git
# cd yamcs-sle && rm -rif .git && cd ..
# cd yamcs-sle && mvn yamcs:run

# git clone https://github.com/yamcs/yamcs.git
# cd yamcs && rm -rif .git && cd ..

# git clone https://github.com/yamcs/jsle.git
# cd jsle && rm -rif .git && cd ..

# git clone https://github.com/yamcs/opi.js.git
# cd opi.js && rm -rif .git && cd ..

# git clone https://github.com/yamcs/yamcs-prometheus.git
# cd yamcs-prometheus && rm -rif .git && cd ..

# git clone https://github.com/yamcs/quickstart.git
# cd quickstart && rm -rif .git && cd ..

# git clone https://github.com/yamcs/grafana-yamcs.git
# cd grafana-yamcs && rm -rif .git && cd ..

# YAMCS DOCS
# cd docs
# wget https://dl.yamcs.org/docs/yamcs-server-manual.pdf
# wget https://dl.yamcs.org/docs/yamcs-studio.pdf
# wget https://dl.yamcs.org/docs/yamcs-maven-plugin.pdf
# wget https://dl.yamcs.org/docs/yamcs-cli.pdf
# wget https://dl.yamcs.org/docs/yamcs-http-api.pdf
# wget https://dl.yamcs.org/docs/python-yamcs-client.pdf
# wget https://dl.yamcs.org/docs/yamcs-sle.pdf
# wget https://dl.yamcs.org/docs/yamcs-prometheus.pdf
# wget https://dl.yamcs.org/docs/yamcs-relnotes.pdf
# wget https://dl.yamcs.org/docs/yamcs-studio-relnotes.pdf
 
# YAMCS STUDIO
# wget https://github.com/yamcs/yamcs-studio/releases/download/v1.6.2/yamcs-studio-1.6.2-linux.gtk.x86_64.tar.gz
# tar -xzvf yamcs-studio-1.6.2-linux.gtk.x86_64.tar.gz
# mv yamcs-studio-1.6.2 yamcs-studio

# YAMCS CLIENT 
# /usr/bin/python3 -m pip install --upgrade pip
# pip install --upgrade yamcs-cli sphinxcontrib-yamcs
# yamcs login http://localhost:8090

cd config/t6a_smu/

export PROXY_WIDTH_START=30

set_proxy_width_start(){ 
	PROXY_WIDTH_START=$(((PROXY_WIDTH_START+800)%1600)); 
	echo "WIDTH:$PROXY_WIDTH_START";
}

set_proxy_width_start
gnome-terminal --title "YAMCS-SERVER" --hide-menubar --geometry=100x24+$PROXY_WIDTH_START+660 -- bash -c "mvn yamcs:run && sleep 10000000";
set_proxy_width_start
gnome-terminal --title "YAMCS-SIM"    --hide-menubar --geometry=100x24+$PROXY_WIDTH_START+660 -- bash -c "python simulator.py";

google-chrome http://localhost:8090



