set terminal png size 800,600
set output out;
set datafile separator ','                                                                                            
plot filename u 1:2 w lp t name
