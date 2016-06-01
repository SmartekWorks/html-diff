Sample Command
==============
java -jar -DsrcZip=S0001-001-1.zip -DdstZip=S0001-001-2.zip -DdomFilter=filters result-diff.jar

Parameters
==========
* srcZip（必须）：源zip文件的路径。
* dstZip（必须）：目标zip文件的路径。
* domFilter（可选）：json格式的filter文件路径。如果该参数为空，则认为filter为空。
* outputFolder（可选）：输出目录的路径，如果已经存在，先删除所有内容。如果该参数为空，则默认导出到当前的result目录
