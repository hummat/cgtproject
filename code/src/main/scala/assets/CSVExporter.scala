package assets

import com.github.tototoshi.csv.CSVWriter

import java.io.File

class CSVExporter {

  def exportCSV(filename:String, exportData:Array[List[_]]):Unit = {
    val f = new File("/"+filename)
    val writer = CSVWriter.open(f)

    for(i <- 0 to exportData.length-1) {
      writer.writeRow(exportData(i))
    }
    writer.close()
  }
}


/*
---- CSVExporter ----

Assume you have two functions f(x) = y1 and g(x) = y2.
If your function's table looks like this:

X  --- Y1 --- Y2 -- Y.
x_0 | y1_0 | y2_0 | ..
x_1 | y1_1 | y2_1 | ..
x_2 | y1_2 | y2_2 | ..
x_3 | y1_3 | y2_3 | ..
x_4 | y1_4 | y2_4 | ..
x_5 | y1_5 | y2_5 | ..

The CSV format can be obtained by passing an Array[List[Any]]
to the function as exportData parameter. Make sure that the elements of the list are of type ANY.
For the above function this would be:

var exportData = Array(List(y1_0:Any, y2_0:Any),
                       List(y1_1:Any, y2_1):Any,
                       List(y1_2:Any, y2_2:Any),
                       List(y1_3:Any, y2_3:Any),
                       List(y1_4:Any, y2_4:Any),
                       List(y1_5:Any, y2_5:Any))


By default it adds plots to the root directory of the project with
the filename (String) parameter as name.
Note that filename can also specify subdirectories.


cgtproject
  - target
  - code
  - report
  - ... <- Added here


--- EXAMPLE WITH DATA ----

val exporter = new CSVExporter()
var exportData = Array(List(5:Any, 7:Any),
                         List(6:Any, 8:Any),
                         List(7:Any, 9:Any),
                         List(8:Any, 10:Any),
                         List(9:Any, 11:Any),
                         List(10:Any, 12:Any))

exporter.exportCSV("testplots.csv", exportData)

*/