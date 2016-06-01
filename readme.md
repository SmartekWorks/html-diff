### Build

`make.sh`

### Usage

`java -DsrcZip=S0001-001-1.zip -DdstZip=S0001-001-2.zip -DdomFilter=filters -jar html-diff.jar`

* srcZip (mandatory)：path to the source HTML zip file.
* dstZip (mandatory)：path to the destination HTML zip file.
* domFilter (optional)：path to the filter configurations in json format. If set to empty string, no filters will be applied.
* outputFolder (optional)：path to the diff result output folder. If folder exists, all contents inside will be purged at first. If set to empty, the current `result` folder will be used.

### How to generate HTML zip files

* Login SWATHub, select your workspace and goto the test set.
* Choose the test cases, open `More` dropdown menu at the right bottom of the page, and click `Export Results to Zip` to generate all the execution HTMLs into one zip file.

### How to setup HTML filters

* The filters file contains a `json` list. Each item of the list is a CSS selector which will exclude blocks matching this selector from the HTML comparison.
* Here is a sample of filters file.

```json
[
  "input[type=hidden]", 
  "div.banner"
]
```



