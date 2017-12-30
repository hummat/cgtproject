package assets

import com.github.tototoshi.csv.CSVWriter

import java.io.File

class CSVExporter {

  def exportCSV(filename:String, exportData:Array[List[_]]):Unit = {
    val f = new File("dataplotting/data/"+filename)
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
to the function as exportData parameter.
 For the above function this would be:

var exportData = Array(List(y1_0, y2_0),
                       List(y1_1, y2_1),
                       List(y1_2, y2_2),
                       List(y1_3, y2_3),
                       List(y1_4, y2_4),
                       List(y1_5, y2_5))


By default it adds plots to the dataplotting directory with
the filename (String) parameter as name.
Note that filename can also specify subdirectories.


cgtproject
  - dataplotting   <- Added here
  - code
  - report
  - ...


--- EXAMPLE WITH ABOVE DATA ----

var exporter = new CSVExporter()
var exportData = ..... (see above)
exporter.exportCSV("testplots.csv", exportData)

*/