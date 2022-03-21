basedir=$1
testname=$2
task=$3
metric=$4
echo "Plotting graph for $2"

python metrics-to-csv.py $basedir/$testname/metrics-stress.json $basedir/$testname/results-stress.json $3 $4 > plot-$testname.csv
gnuplot -e "filename='plot-$testname.csv'" -e "name='$testname'" -e "out='$testname.png'" metrics.gnuplot
