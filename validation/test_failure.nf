nextflow.enable.dsl=2

process failProcess {
    script:
    """
    echo "Starting a process that will fail deliberately..."
    cat nonexistent_file.txt
    """
}

workflow {
    failProcess()
}
