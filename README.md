# In-place Matrix Inversion

_Invertigo_ is a project that provides: 
* a description of the in-place matrix inversion algorithm
* serial and parallel implementations of the algorithm
* technical context for my other projects to experiment with different approaches to parallel computing

## Overview
\<to be added>

## Algorithm description
\<to be added>

## Finite fields
With floating-point numbers, it's quite usual that no pivot element is exactly zero so the reciprocal is always defined.
The goal in that case is to avoid computational instability due to dividing by a number with close to zero absolute value. 
Instead of looking for a non-zero pivot element in the algorithm, we should pick the one with the maximum absolute value.
Of course, if the original matrix is essentially singular, at some step, all pivot candidates will be very close to zero and further computation will inevitably become unstable.  
 
_DoubleInverse_ is a self-contained class providing a serial implementation of the in-place matrix inversion algorithm for type _double_.
In the _checkInverted_ method, the maximum absolute error value is computed. See what it may be with _RandomSingularMatrix_. 

To avoid being _floated away_ with floating-point computations I chose the precise arithmetic of [finite fields](https://en.wikipedia.org/wiki/Finite_field). 
Although basic operations become an order of magnitude more expensive that is irrelevant for scalability analysis, which is my main goal in this excersice.
  
\<to be expanded>

## Serial implementation
\<to be added>

## Straightforward parallel implementation
All iterations in the outer loop are considered inherently serial.
To prevent one iteration to start executing before the previous one is done during parallel execution, I introduce a barrier for all threads to synchronize on between iterations.

\<to be continued>

## Parallel wait-free implementation
The outer loop being inherently serial assumption of the straightforward implementation is absolutely artificial.
Once row _k_ is ready in iteration _k_ all other rows can proceed with that iteration. 
Row _k+1_ may be computed for iteration _k+1_ before other rows are finished in iteration _k_. So, any row that is done in _k_ by this moment could proceed with _k+1_? 
Except for row _k_ which values are still being used by remaining rows in iteration _k_. Extend this logic to further iterations and the whole picture becomes very convoluted.
Yet one see that there is extra concurrency, if only it could be brought out.   

\<to be continued>

## Analysis
\<to be added>
